package dev.vexsoft.core.paper.inventory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Shared native player storage transfers that leave equipment and menu slots untouched. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerInventoryTransfers {

    /** Moves between hotbar and backpack without equipping items; invoke on the owning player thread. */
    public static void shift(final PlayerInventory inventory, final int source) {
        if (source < 0 || source >= 36) {
            return;
        }

        ItemStack original = inventory.getItem(source);

        if (original == null || original.isEmpty()) {
            return;
        }

        ItemStack remaining = original.clone();
        int start = source < 9 ? 9 : 0;
        int end = source < 9 ? 36 : 9;

        for (int slotIndex = start; slotIndex < end && !remaining.isEmpty(); slotIndex++) {
            ItemStack target = inventory.getItem(slotIndex);

            if (target == null || !target.isSimilar(remaining)) {
                continue;
            }

            int count = Math.min(remaining.getAmount(), Math.max(0, target.getMaxStackSize() - target.getAmount()));

            if (count == 0) {
                continue;
            }

            ItemStack updated = target.clone();

            updated.setAmount(target.getAmount() + count);
            inventory.setItem(slotIndex, updated);
            remaining.setAmount(remaining.getAmount() - count);
        }

        for (int slotIndex = start; slotIndex < end && !remaining.isEmpty(); slotIndex++) {
            ItemStack target = inventory.getItem(slotIndex);

            if (target != null && !target.isEmpty()) {
                continue;
            }

            int count = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();

            placed.setAmount(count);
            inventory.setItem(slotIndex, placed);
            remaining.setAmount(remaining.getAmount() - count);
        }

        inventory.setItem(source, remaining.isEmpty() ? null : remaining);
    }
}
