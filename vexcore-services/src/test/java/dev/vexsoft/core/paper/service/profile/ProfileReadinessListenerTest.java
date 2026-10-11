package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** Verifies interaction guards and the independent movement boundary. */
class ProfileReadinessListenerTest {

    @Test
    void fillingAndEmptyingAreBlockedUntilTheSessionIsReady() {
        var fixture = new Fixture(true, false);
        fixture.listener.fillBucket(fixture.fill);
        fixture.listener.emptyBucket(fixture.empty);
        assertTrue(fixture.fill.isCancelled());
        assertTrue(fixture.empty.isCancelled());
    }

    @Test
    void fillingAndEmptyingRemainAvailableForReadySessions() {
        var fixture = new Fixture(true, true);
        fixture.listener.fillBucket(fixture.fill);
        fixture.listener.emptyBucket(fixture.empty);
        assertFalse(fixture.fill.isCancelled());
        assertFalse(fixture.empty.isCancelled());
    }

    @Test
    void anUnloadedAccountCannotMutateBucketContents() {
        var fixture = new Fixture(false, true);
        fixture.listener.fillBucket(fixture.fill);
        fixture.listener.emptyBucket(fixture.empty);
        assertTrue(fixture.fill.isCancelled());
        assertTrue(fixture.empty.isCancelled());
    }

    @Test
    void pendingWritesAllowMovementWhileInventoryInteractionsRemainBlocked() {
        var fixture = new Fixture(true, false, true);
        fixture.listener.move(fixture.move);
        fixture.listener.fillBucket(fixture.fill);
        assertFalse(fixture.move.isCancelled());
        assertTrue(fixture.fill.isCancelled());
    }

    @Test
    void restoringSessionsAndUnloadedAccountsCannotMove() {
        var restoring = new Fixture(true, false, false);
        restoring.listener.move(restoring.move);
        assertTrue(restoring.move.isCancelled());

        var unloaded = new Fixture(false, true, true);
        unloaded.listener.move(unloaded.move);
        assertTrue(unloaded.move.isCancelled());
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture {

        private final ProfileReadinessListener listener;
        private final PlayerBucketFillEvent fill;
        private final PlayerBucketEmptyEvent empty;
        private final PlayerMoveEvent move;

        private Fixture(final boolean loaded, final boolean ready) {
            this(loaded, ready, ready);
        }

        private Fixture(final boolean loaded, final boolean ready, final boolean movementAllowed) {
            UUID playerId = UUID.randomUUID();
            var owner = new VexPlayer(playerId, "Alex", type -> -1);
            var player = proxy(Player.class, (object, method, arguments) -> playerId);
            var block = proxy(Block.class, (object, method, arguments) -> null);
            var players = proxy(PlayerService.class, (object, method, arguments) ->
                loaded ? Optional.of(owner) : Optional.empty());
            var profiles = proxy(ProfileSessionService.class, (object, method, arguments) ->
                method.getName().equals("isMovementAllowed") ? movementAllowed : ready);
            var services = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                arguments[0] == PlayerService.class ? players : profiles);
            listener = new ProfileReadinessListener(services);
            fill = new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.BUCKET, null,
                EquipmentSlot.HAND);
            empty = new PlayerBucketEmptyEvent(player, block, block, BlockFace.UP, Material.WATER_BUCKET, null,
                EquipmentSlot.HAND);
            move = new PlayerMoveEvent(player, new Location(null, 0, 80, 0), new Location(null, 1, 80, 0));
        }
    }
}
