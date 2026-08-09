package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryKey;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class VexInventoryHolder implements InventoryHolder {

  @Getter
  private final UUID viewerId;
  @Getter
  @Setter
  private InventoryKey inventoryKey;
  private Inventory inventory;

  VexInventoryHolder(final UUID viewerId, final InventoryKey inventoryKey) {
    this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
    this.inventoryKey = Objects.requireNonNull(inventoryKey, "inventoryKey");
  }

  void attach(final Inventory inventory) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  @Override
  public @NotNull Inventory getInventory() {
    if (inventory == null) {
      throw new IllegalStateException("Inventory has not been attached yet");
    }
    return inventory;
  }
}
