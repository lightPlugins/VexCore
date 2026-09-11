package dev.vexsoft.core.paper.inventory;

import java.util.function.BooleanSupplier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Native inventory rollback companion for synchronous player-data transactions. */
public final class InventoryTransactions {

  private InventoryTransactions() {
  }

  /** Restores copied contents on failure or exception; invoke only on the player's owning thread. */
  public static boolean withRollback(
      final PlayerInventory inventory,
      final BooleanSupplier operation
  ) {
    ItemStack[] before = inventory.getContents().clone();
    for (int index = 0; index < before.length; index++) {
      ItemStack item = before[index];
      before[index] = item == null ? null : item.clone();
    }
    boolean successful = false;
    try {
      successful = operation.getAsBoolean();
      return successful;
    } finally {
      if (!successful) {
        inventory.setContents(before);
      }
    }
  }
}
