package dev.vexsoft.core.common.service.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.configuration.MapConfigurationSection;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.Reward;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Checks shared named reward envelopes and selection reuse across transaction retries. */
final class VexRewardPreparationTest {

    @Test
    void freezesSelectedAmountsOnceAndKeepsTheAnnouncementChance() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger granted = new AtomicInteger();
        CompiledReward action = new CompiledReward() {
            @Override
            public RewardBehavior getBehavior() {
                return RewardBehavior.ACTION;
            }

            @Override
            public CompiledReward prepare(final PlayerExecutionContext context) {
                prepared.incrementAndGet();
                return this;
            }

            @Override
            public RewardResult grant(final PlayerExecutionContext context) {
                granted.incrementAndGet();
                return RewardResult.success();
            }

            @Override
            public Component describe(final PlayerExecutionContext context) {
                return Component.text("Chestplate +5");
            }
        };
        var service = service(value -> action);
        var compiled = service.compile(new MapConfigurationSection(Map.of(
            "never", Map.of("type", "items", "value", 1, "chance", 0.0),
            "legendary", Map.of("type", "items", "value", 1, "chance", 1.0,
                "announce", true, "announce-type", "legendary_drop"))));
        var context = new PlayerExecutionContext(new VexPlayer(UUID.randomUUID(), "Alex"), Map.of());
        var selection = service.prepareActions(compiled, context);
        assertEquals(1, selection.entries().size());
        assertEquals("legendary_drop", selection.entries().getFirst().options().announcement());
        assertEquals(1.0, selection.entries().getFirst().options().chance());
        assertTrue(service.grantPrepared(selection, context).isSuccessful());
        assertTrue(service.grantPrepared(selection, context).isSuccessful());
        assertEquals(1, prepared.get());
        assertEquals(2, granted.get());
    }

    @Test
    void rejectsMissingOrInvalidChancesAndUnknownAnnouncementOptions() {
        var service = service(value -> null);
        for (Object chance : new Object[]{-0.1, 1.1, Double.NaN, "1%"}) {
            assertThrows(IllegalArgumentException.class, () -> service.compile(new MapConfigurationSection(
                Map.of("rare", Map.of("type", "items", "value", 1, "chance", chance)))));
        }
        assertThrows(IllegalArgumentException.class, () -> service.compile(new MapConfigurationSection(
            Map.of("rare", Map.of("type", "items", "value", 1)))));
    }

    private static VexRewardService service(final Reward handler) {
        var components = (ExecutionComponentCoordinatorService) Proxy.newProxyInstance(
            ExecutionComponentCoordinatorService.class.getClassLoader(),
            new Class<?>[]{ExecutionComponentCoordinatorService.class},
            (instance, method, arguments) -> Optional.of(handler));
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> components);
        return new VexRewardService(registry);
    }
}
