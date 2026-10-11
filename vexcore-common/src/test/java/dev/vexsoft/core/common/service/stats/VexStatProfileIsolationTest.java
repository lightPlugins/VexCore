package dev.vexsoft.core.common.service.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies that cached stat views and modifier handles cannot follow a player into another profile. */
class VexStatProfileIsolationTest {

    @Test
    void switchingRetiresPersonalViewsAndModifiersWhileAccountStatsKeepTheirIdentity() {
        var domain = new ProfileDomain("test:game");
        var player = new VexPlayer(UUID.randomUUID(), "Alex", type -> type == StatContainer.class ? 0 : -1);
        var definition = StatDefinition.builder(StatKey.of("test", "health")).defaultValue(100)
            .dataScope(PlayerDataScope.profile(domain)).build();
        var key = GameplayPlayerData.key(definition);
        var first = key.createDefaultValue();
        var second = key.createDefaultValue();
        first.getPermanentValues().put("test:health", 10D);
        second.getPermanentValues().put("test:health", 20D);
        player.install(GameplayPlayerData.STATS, new StatData());
        player.installProfile(context(player, domain), Map.of(key, first));
        var coordinator = (PlayerDataCoordinatorService) Proxy.newProxyInstance(
            PlayerDataCoordinatorService.class.getClassLoader(), new Class<?>[]{PlayerDataCoordinatorService.class},
            (proxy, method, arguments) -> null);
        ServiceOwner owner = () -> "test";
        var services = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (proxy, method, arguments) ->
                method.getName().equals("getOwner") ? owner : coordinator);
        var registry = new VexStatRegistryCoordinatorService(services);
        var scoped = registry.register(owner, definition);
        var account = registry.register(owner, StatDefinition.builder(StatKey.of("test", "account")).build());
        var container = new VexStatContainer(player, registry);
        player.installContainer(0, StatContainer.class, container);
        var original = container.getStat(scoped);
        var modifier = original.addModifier(StatModifier.flat(5));
        var accountView = container.getStat(account);
        accountView.setPermanent(42);
        assertEquals(115, original.getValue());
        player.installProfile(context(player, domain), Map.of(key, second));
        assertThrows(IllegalStateException.class, () -> original.setPermanent(999));
        assertFalse(modifier.isActive());
        var current = container.getStat(scoped);
        current.addModifier(StatModifier.flat(3));
        modifier.remove();
        assertEquals(123, current.getValue());
        assertEquals(42, accountView.getPermanent());
        player.installProfile(context(player, domain), Map.of(key, first));
        assertEquals(110, container.getStat(scoped).getValue());
        container.close();
    }

    private static PlayerProfileContext context(final VexPlayer player, final ProfileDomain domain) {
        return new PlayerProfileContext(domain, UUID.randomUUID(), player.getUniqueId(), UUID.randomUUID(), 1, 1, 1);
    }
}
