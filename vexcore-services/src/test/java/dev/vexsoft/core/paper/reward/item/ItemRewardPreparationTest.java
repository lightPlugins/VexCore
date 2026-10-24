package dev.vexsoft.core.paper.reward.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
import dev.vexsoft.core.reward.RewardResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Ensures alternate item destinations receive frozen, independent drops without inventory delivery. */
final class ItemRewardPreparationTest {

    @Test
    void storageModeReloadCannotMaterializeAnUnboundedPreviouslyVirtualReward() {
        var virtual = new AtomicBoolean(true);
        var items = proxy(RewardItemService.class, (instance, method, arguments) -> switch (method.getName()) {
            case "validate" -> null;
            case "isVirtual" -> virtual.get();
            case "describe" -> Component.text("Ore");
            default -> throw new AssertionError("Unexpected native access: " + method.getName());
        });
        var registry = proxy(VexServiceRegistry.class, (instance, method, arguments) -> items);
        var owner = new VexPlayer(UUID.randomUUID(), "Alex");
        owner.bindPlatformPlayer(proxy(Player.class, (instance, method, arguments) -> null));
        var reward = new ItemReward(registry).compile(Map.of("key", "vexisles:ore", "amount", "1zz"));
        virtual.set(false);
        assertThrows(IllegalArgumentException.class, () -> reward.prepare(new PlayerExecutionContext(owner, Map.of())));
    }

    @Test
    void virtualRangesAreFrozenOnceAndNeverCreateOrDeliverNativeStacks() {
        WholeAmount[] deposited = {WholeAmount.ZERO};
        var items = proxy(RewardItemService.class, (instance, method, arguments) -> switch (method.getName()) {
            case "validate" -> null;
            case "isVirtual" -> true;
            case "name" -> Component.text("Ore");
            case "describe" -> Component.text(arguments[2] + " Ore");
            case "deliverVirtual" -> {
                deposited[0] = deposited[0].add((WholeAmount) arguments[2]);
                yield null;
            }
            default -> throw new AssertionError("Virtual preparation touched native delivery: " + method.getName());
        });
        var registry = proxy(VexServiceRegistry.class, (instance, method, arguments) -> items);
        var owner = new VexPlayer(UUID.randomUUID(), "Alex");
        owner.bindPlatformPlayer(proxy(Player.class, (instance, method, arguments) -> null));
        var context = new PlayerExecutionContext(owner, Map.of());
        var prepared = assertInstanceOf(PreparedItemReward.class, new ItemReward(registry)
            .compile(Map.of("key", "vexisles:ore", "amount", Map.of("min", "3zz", "max", "5zz"))).prepare(context));
        assertEquals(List.of(), prepared.preparedItems());
        var description = prepared.describe(context);
        assertEquals(description, prepared.describe(context));
        assertEquals(WholeAmount.ZERO, deposited[0]);
        assertEquals(RewardResult.Status.SUCCESS, prepared.grant(context).status());
        org.junit.jupiter.api.Assertions.assertTrue(deposited[0].compareTo(WholeAmountFormatter.parse("3zz")) >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(deposited[0].compareTo(WholeAmountFormatter.parse("5zz")) <= 0);
    }

    @Test
    void exposesIndependentStacksAndPreservesThePreparedGrantAfterCallerMutation() {
        var deliveries = new AtomicInteger();
        var deliveredAmount = new AtomicInteger();
        var items = proxy(RewardItemService.class, (instance, method, arguments) -> switch (method.getName()) {
            case "validate" -> null;
            case "isVirtual" -> false;
            case "create" -> new Stack(1);
            case "name" -> Component.text("Iron");
            case "describe" -> Component.text(arguments[2] + "× ")
                .append(Component.translatable("item.minecraft.raw_iron"));
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
