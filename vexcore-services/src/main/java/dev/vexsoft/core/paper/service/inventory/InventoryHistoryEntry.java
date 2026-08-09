package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.InventoryView;
import java.util.Objects;
import lombok.Getter;

@Getter
final class InventoryHistoryEntry {

  private final InventoryKey key;
  private final InventoryView view;

  InventoryHistoryEntry(final InventoryView view) {
    this.view = Objects.requireNonNull(view, "view");
    this.key = view.getKey();
  }
}
