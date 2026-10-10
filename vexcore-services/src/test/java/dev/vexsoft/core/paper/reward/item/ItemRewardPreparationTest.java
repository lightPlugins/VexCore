package dev.vexsoft.core.paper.reward.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
import dev.vexsoft.core.reward.RewardResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Ensures alternate item destinations receive frozen, independent drops without inventory delivery. */
final class ItemRewardPreparationTest {

    @Test
    void exposesIndependentStacksAndPreservesThePreparedGrantAfterCallerMutation() {
        var deliveries = new AtomicInteger();
        var deliveredAmount = new AtomicInteger();
        var items = proxy(RewardItemService.class, (instance, method, arguments) -> switch (method.getName()) {
            case "validate" -> null;
            case "create" -> new Stack(1);
            case "name" -> Component.text("Iron");
            case "deliver" -> {
                deliveries.incrementAndGet();
                @SuppressWarnings("unchecked")
                var stacks = (List<ItemStack>) arguments[1];
                deliveredAmount.set(stacks.stream().mapToInt(ItemStack::getAmount).sum());
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        var registry = proxy(VexServiceRegistry.class, (instance, method, arguments) -> items);
        var owner = new VexPlayer(UUID.randomUUID(), "Alex");
        owner.bindPlatformPlayer(proxy(Player.class, (instance, method, arguments) -> null));
        var context = new PlayerExecutionContext(owner, Map.of());
        var prepared = assertInstanceOf(PreparedItemReward.class,
            new ItemReward(registry).compile(Map.of("key", "minecraft:raw_iron", "amount", 80)).prepare(context));
        assertEquals(List.of(64, 16), prepared.preparedItems().stream().map(ItemStack::getAmount).toList());
        prepared.preparedItems().getFirst().setAmount(1);
        assertEquals(List.of(64, 16), prepared.preparedItems().stream().map(ItemStack::getAmount).toList());
        assertEquals(0, deliveries.get());
        assertEquals(RewardResult.Status.SUCCESS, prepared.grant(context).status());
        assertEquals(80, deliveredAmount.get());
        assertEquals(1, deliveries.get());
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Stack extends ItemStack {

        private int amount;

        private Stack(final int amount) {
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(final int amount) {
            this.amount = amount;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public Stack clone() {
            return new Stack(amount);
        }
    }
}
