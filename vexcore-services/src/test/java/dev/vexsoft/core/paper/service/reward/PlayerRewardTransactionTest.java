package dev.vexsoft.core.paper.service.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.reward.VexRewardService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Ensures player-data transactions also compensate physical item delivery and cursor mutations. */
public final class PlayerRewardTransactionTest {

    @Test
    void failedAndThrowingRewardsRestoreClonedInventoryAndCursor() {
        Fixture fixture = new Fixture();
        assertFalse(fixture.transactions.execute(
            fixture.player, () -> {
                fixture.inventory[0].setAmount(64);
                fixture.cursor.setAmount(20);
                return false;
            }
        ));
        assertEquals(2, fixture.inventory[0].getAmount());
        assertEquals(1, fixture.cursor.getAmount());
        assertThrows(
            IllegalStateException.class, () -> fixture.transactions.execute(
                fixture.player, () -> {
                    fixture.inventory[0].setAmount(50);
                    throw new IllegalStateException("Second reward failed");
                }
            )
        );
        assertEquals(2, fixture.inventory[0].getAmount());
    }

    @Test
    void successfulDeliveryKeepsInventoryChanges() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.transactions.execute(
            fixture.player, () -> {
                fixture.inventory[0].setAmount(10);
                return true;
            }
        ));
        assertEquals(10, fixture.inventory[0].getAmount());
    }

    private static final class Fixture {

        private ItemStack[] inventory = {new Stack(2)};
        private ItemStack cursor = new Stack(1);
        private final PlayerInventory platformInventory = proxy(
            PlayerInventory.class, (object, method, arguments) ->
                switch (method.getName()) {
                    case "getContents" -> inventory;
                    case "setContents" -> {
                        inventory = (ItemStack[]) arguments[0];
                        yield null;
                    }
                    default -> null;
                }
        );
        private final Player platform = proxy(
            Player.class, (object, method, arguments) -> switch (method.getName()) {
                case "getInventory" -> platformInventory;
                case "getItemOnCursor" -> cursor;
                case "setItemOnCursor" -> {
                    cursor = (ItemStack) arguments[0];
                    yield null;
                }
                default -> null;
            }
        );
        private final ExecutionComponentCoordinatorService components = proxy(
            ExecutionComponentCoordinatorService.class,
            (object, method, arguments) -> null
        );
        private final VexServiceRegistry registry = proxy(
            VexServiceRegistry.class,
            (object, method, arguments) -> arguments[0] == RewardService.class ? this.rewards : components
        );
        private final VexRewardService rewards = new VexRewardService(registry);
        private final VexPlayerRewardTransactionService transactions = new VexPlayerRewardTransactionService(registry);
        private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");

        private Fixture() {
            player.bindPlatformPlayer(platform);
        }
    }

    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    private static final class Stack extends ItemStack {

        @Getter(onMethod_ = @Override)
        @Setter(onMethod_ = @Override)
        private int amount;

        @Override
        public ItemStack clone() {
            return new Stack(amount);
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
