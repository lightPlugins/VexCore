package dev.vexsoft.core.common.service.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.stats.contribution.VexStatContributionCoordinatorService;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises mixed source snapshots against the actual player stat container and registry. */
public final class VexStatContributionTest {

    private static final StatKey HEALTH = StatKey.of("test", "health");

    @Test
    void appliesReplacesAndRemovesAllOperationsFromTheSameSource() {
        try (Fixture fixture = new Fixture()) {
            fixture.bonuses = Map.of(HEALTH, List.of(
                StatModifier.flat(50), StatModifier.additiveMultiplier(0.1)));
            assertTrue(fixture.contributions.refresh(fixture.player, fixture.owner, "equipment").successful());
            assertEquals(165.0D, fixture.value(), 0.000001D);
            assertTrue(fixture.contributions.refresh(fixture.player, fixture.owner, "equipment").successful());
            assertEquals(165.0D, fixture.value(), 0.000001D);
            fixture.bonuses = Map.of(HEALTH, List.of(StatModifier.additiveMultiplier(0.25)));
            fixture.contributions.refresh(fixture.player, fixture.owner, "equipment");
            assertEquals(125.0D, fixture.value(), 0.000001D);
            fixture.bonuses = Map.of();
            fixture.contributions.refresh(fixture.player, fixture.owner, "equipment");
            assertEquals(100.0D, fixture.value(), 0.000001D);
        }
    }

    @Test
    void retainsTheAppliedSnapshotWhenAReplacementContainsAnUnknownStat() {
        try (Fixture fixture = new Fixture()) {
            fixture.bonuses = Map.of(HEALTH, List.of(StatModifier.flat(50), StatModifier.additiveMultiplier(0.1)));
            fixture.contributions.refresh(fixture.player, fixture.owner, "equipment");
            fixture.bonuses = Map.of(StatKey.of("test", "missing"), List.of(StatModifier.flat(5)));
            assertFalse(fixture.contributions.refresh(fixture.player, fixture.owner, "equipment").successful());
            assertEquals(165.0D, fixture.value(), 0.000001D);
            fixture.contributions.removePlayer(fixture.player);
            assertEquals(100.0D, fixture.value(), 0.000001D);
        }
    }

    @Test
    void preservesExistingSingleModifierProviders() {
        try (Fixture fixture = new Fixture()) {
            fixture.contributions.register(fixture.owner, fixture.services, "legacy", SingleProvider.class);
            assertTrue(fixture.contributions.refresh(fixture.player, fixture.owner, "legacy").successful());
            assertEquals(110.0D, fixture.value(), 0.000001D);
            fixture.contributions.unregister(fixture.owner, "legacy");
            assertEquals(100.0D, fixture.value(), 0.000001D);
        }
    }

    /** Supplies test snapshots without persistent provider state. */
    public interface DesiredBonuses extends VexService {

        /** Returns the next desired test contribution snapshot. */
        Map<StatKey, List<StatModifier>> snapshot();
    }

    @Dependencies(DesiredBonuses.class)
    public static final class MixedProvider implements StatContributionProvider {

        private final DesiredBonuses bonuses;

        public MixedProvider(final VexServiceRegistry services) {
            bonuses = services.require(DesiredBonuses.class);
        }

        @Override
        public Map<StatKey, StatModifier> calculate(final VexPlayer player) {
            throw new AssertionError("The coordinator must use the complete modifier snapshot");
        }

        @Override
        public Map<StatKey, List<StatModifier>> calculateModifiers(final VexPlayer player) {
            return bonuses.snapshot();
        }
    }

    @Dependencies
    public static final class SingleProvider implements StatContributionProvider {

        public SingleProvider(final VexServiceRegistry services) {
        }

        @Override
        public Map<StatKey, StatModifier> calculate(final VexPlayer player) {
            return Map.of(HEALTH, StatModifier.flat(10));
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final ServiceOwner owner = () -> "test";
        private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex",
            type -> type == StatContainer.class ? 0 : -1);
        private final VexServiceRegistry services;
        private final VexStatContributionCoordinatorService contributions;
        private final VexStatContainer container;
        private final Stat health;
        private Map<StatKey, List<StatModifier>> bonuses = Map.of();

        private Fixture() {
            Map<Class<?>, Object> managed = new HashMap<>();
            services = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
                new Class<?>[] {VexServiceRegistry.class}, (instance, method, arguments) -> switch (method.getName()) {
                    case "require" -> managed.get(arguments[0]);
                    case "isAvailable" -> managed.containsKey(arguments[0]);
                    default -> throw new AssertionError(method.getName());
                });
            var stats = new VexStatRegistryCoordinatorService(services);
            managed.put(StatRegistryCoordinatorService.class, stats);
            managed.put(DesiredBonuses.class, (DesiredBonuses) () -> bonuses);
            managed.put(PlayerDataCoordinatorService.class, Proxy.newProxyInstance(
                PlayerDataCoordinatorService.class.getClassLoader(), new Class<?>[] {PlayerDataCoordinatorService.class},
                (instance, method, arguments) -> List.of(player)));
            player.install(GameplayPlayerData.STATS, new StatData());
            health = stats.register(owner, StatDefinition.builder(HEALTH).defaultValue(100).minimum(1).build());
            container = new VexStatContainer(player, stats);
            player.installContainer(0, StatContainer.class, container);
            contributions = new VexStatContributionCoordinatorService(services);
            contributions.register(owner, services, "equipment", MixedProvider.class);
        }

        private double value() {
            return container.getStat(health).getValue();
        }

        @Override
        public void close() {
            contributions.close();
            container.close();
        }
    }
}
