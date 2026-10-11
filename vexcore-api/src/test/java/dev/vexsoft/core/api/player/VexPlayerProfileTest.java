package dev.vexsoft.core.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VexPlayerProfileTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("game:progress");
    private static final DataContainerKey<StringBuilder> SCOPED = DataContainerKey.of(
        "progress", StringBuilder.class, StringBuilder::new, PlayerDataScope.profile(DOMAIN));
    private static final DataContainerKey<StringBuilder> ACCOUNT = DataContainerKey.of(
        "preferences", StringBuilder.class, StringBuilder::new);

    @Test
    void missingContextFailsClosedAndAccountDataSurvivesSwitches() {
        VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");
        player.install(ACCOUNT, new StringBuilder("language"));
        player.install(SCOPED, new StringBuilder("legacy"));
        assertThrows(IllegalStateException.class, () -> player.read(SCOPED, Object::toString));

        PlayerProfileContext first = context(player, UUID.randomUUID(), 1);
        player.installProfile(first, Map.of(SCOPED, new StringBuilder("first")));
        player.installProfile(context(player, UUID.randomUUID(), 2), Map.of(SCOPED, new StringBuilder("second")));

        assertEquals("second", player.read(SCOPED, Object::toString));
        assertEquals("language", player.read(ACCOUNT, Object::toString));
        assertEquals(player.getUniqueId(), player.addressOf(ACCOUNT).playerId());
        assertFalse(player.isCurrentContext(first));
    }

    @Test
    void staleAcknowledgementCannotCleanReactivatedProfileAtSameRevision() {
        VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");
        UUID profile = UUID.randomUUID();
        PlayerProfileContext old = context(player, profile, 1);
        player.installProfile(old, Map.of(SCOPED, new StringBuilder()));
        player.update(SCOPED, value -> {
            value.append("old");
        });
        long revision = player.snapshot(SCOPED, Object::toString).getRevision();
        player.installProfile(context(player, profile, 3), Map.of(SCOPED, new StringBuilder()));
        player.update(SCOPED, value -> {
            value.append("new");
        });
        player.markClean(SCOPED, old, revision);
        assertTrue(player.getDirtyKeys().contains(SCOPED));
        player.markClean(SCOPED, player.findProfileContext(DOMAIN).orElseThrow(), revision);
        assertFalse(player.getDirtyKeys().contains(SCOPED));
    }

    private static PlayerProfileContext context(final VexPlayer player, final UUID profile, final long generation) {
        return new PlayerProfileContext(DOMAIN, profile, player.getUniqueId(), UUID.randomUUID(), generation, 1, 1);
    }
}
