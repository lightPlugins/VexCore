package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Verifies partial storage transfers without touching equipment or losing stack remainders. */
final class PlayerInventoryTransfersTest {

    @Test
    void mergesBeforeUsingEmptySlotsAndPreservesEquipment() {
        ItemStack[] slots = new ItemStack[41];
        slots[9] = new Stack(20);
        slots[2] = new Stack(60);
        slots[38] = new Stack(1);
        var armor = slots[38];

        PlayerInventoryTransfers.shift(inventory(slots), 9);

        assertEquals(64, slots[2].getAmount());
        assertEquals(16, slots[0].getAmount());
        assertNull(slots[9]);
        assertSame(armor, slots[38]);
    }

    @Test
    void partiallyFullDestinationKeepsTheExactRemainder() {
        ItemStack[] slots = new ItemStack[41];
        for (int slot = 0; slot < 9; slot++) {
            slots[slot] = new Stack(64);
        }
        slots[4] = new Stack(61);
        slots[9] = new Stack(20);

        PlayerInventoryTransfers.shift(inventory(slots), 9);

        assertEquals(64, slots[4].getAmount());
        assertEquals(17, slots[9].getAmount());
    }

    @Test
    void hotbarMovesIntoBackpackAndEquipmentCannotBecomeASource() {
        ItemStack[] slots = new ItemStack[41];
        slots[0] = new Stack(3);
        slots[38] = new Stack(1);
        var armor = slots[38];
        var inventory = inventory(slots);

        PlayerInventoryTransfers.shift(inventory, 0);
        PlayerInventoryTransfers.shift(inventory, 38);
        PlayerInventoryTransfers.shift(inventory, -1);

        assertNull(slots[0]);
        assertEquals(3, slots[9].getAmount());
        assertSame(armor, slots[38]);
    }

    private static PlayerInventory inventory(final ItemStack[] slots) {
        return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
            new Class<?>[]{PlayerInventory.class}, (instance, method, arguments) -> switch (method.getName()) {
                case "getItem" -> slots[(int) arguments[0]];
                case "setItem" -> {
                    slots[(int) arguments[0]] = (ItemStack) arguments[1];
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
    }

    @AllArgsConstructor
    private static final class Stack extends ItemStack {

        @Getter(onMethod_ = @Override)
        @Setter(onMethod_ = @Override)
        private int amount;

        @Override
        public boolean isEmpty() {
            return amount == 0;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public boolean isSimilar(final ItemStack other) {
            return other instanceof Stack;
        }

        @Override
        public Stack clone() {
            return new Stack(amount);
        }
    }
}
