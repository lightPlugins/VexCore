package dev.vexsoft.core.paper.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Projectile;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/** Prevents delayed projectile damage and physical arrow recovery from crossing profile boundaries. */
class ProfileProjectileRealmTest {

    @Test
    void switchedGenerationCannotDealDamageWhileTheOriginalCoopMayRecoverItsArrow() {
        var original = context(UUID.randomUUID(), UUID.randomUUID(), 1);
        var projectile = projectile();
        ProfileProjectileRealm.bind(projectile, original);
        assertTrue(ProfileProjectileRealm.current(projectile, original));

        var switched = new PlayerProfileContext(original.domain(), original.profileId(), original.playerId(),
            original.sessionId(), 2, 1, 2);
        assertFalse(ProfileProjectileRealm.current(projectile, switched));
        assertTrue(ProfileProjectileRealm.permitsPickup(projectile, switched));
        var teammate = context(original.profileId(), UUID.randomUUID(), 1);
        assertFalse(ProfileProjectileRealm.current(projectile, teammate));
        assertTrue(ProfileProjectileRealm.permitsPickup(projectile, teammate));
        assertFalse(ProfileProjectileRealm.permitsPickup(projectile, context(UUID.randomUUID(), original.playerId(), 1)));
        assertFalse(ProfileProjectileRealm.permitsPickup(projectile, null));
    }

    @Test
    void ordinaryAccountProjectilesRemainUsableWithoutProfileRegistration() {
        var projectile = projectile();
        assertTrue(ProfileProjectileRealm.current(projectile, null));
        assertTrue(ProfileProjectileRealm.permitsPickup(projectile, null));
        assertTrue(ProfileProjectileRealm.permitsPickup(projectile, context(UUID.randomUUID(), UUID.randomUUID(), 1)));
    }

    private static PlayerProfileContext context(final UUID profile, final UUID player, final long generation) {
        return new PlayerProfileContext(new ProfileDomain("test:game"), profile, player, UUID.randomUUID(), generation, 1, 1);
    }

    private static Projectile projectile() {
        Map<NamespacedKey, Object> values = new HashMap<>();
        var data = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
            new Class<?>[]{PersistentDataContainer.class}, (object, method, arguments) -> switch (method.getName()) {
                case "get" -> values.get(arguments[0]);
                case "has" -> values.containsKey(arguments[0]);
                case "set" -> {
                    values.put((NamespacedKey) arguments[0], arguments[2]);
                    yield null;
                }
                default -> null;
            });
        return (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(), new Class<?>[]{Projectile.class},
            (object, method, arguments) -> data);
    }
}
