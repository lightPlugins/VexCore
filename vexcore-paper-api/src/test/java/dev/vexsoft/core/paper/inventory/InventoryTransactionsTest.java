package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

final class InventoryTransactionsTest {

    @Test
    void failedAndThrowingOperationsRestoreIndependentStackCopies() {
        ItemStack[] slots = {new Stack(4)};
        PlayerInventory inventory = inventory(slots);

        assertFalse(InventoryTransactions.withRollback(
            inventory,
            () -> {
                slots[0].setAmount(99);

                return false;
            }
        ));
        assertEquals(4, slots[0].getAmount());
        assertThrows(
            IllegalStateException.class,
            () -> InventoryTransactions.withRollback(
                inventory,
                () -> {
                    slots[0] = null;
                    throw new IllegalStateException("Delivery failed");
                }
            )
        );
        assertEquals(4, slots[0].getAmount());
        assertTrue(InventoryTransactions.withRollback(
            inventory,
            () -> {
                slots[0].setAmount(7);

                return true;
            }
        ));
        assertEquals(7, slots[0].getAmount());
    }

    private static PlayerInventory inventory(final ItemStack[] contents) {
        return (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[]{PlayerInventory.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getContents" -> contents.clone();
                case "setContents" -> {
                    System.arraycopy(arguments[0], 0, contents, 0, contents.length);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
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
        public ItemStack clone() {
            return new Stack(amount);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Stack stack && amount == stack.amount;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(amount);
        }
    }
}
