package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryView;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class VexInventoryRenderer {

  Inventory render(
      final InventoryContext context,
      final InventoryView view,
      final VexInventoryHolder holder,
      final Map<Integer, InventoryElement> elements
  ) {
    Inventory inventory = Bukkit.createInventory(
        Objects.requireNonNull(holder, "holder"),
        view.getSize(),
        view.getTitle(context)
    );
    holder.attach(inventory);
    holder.setInventoryKey(view.getKey());
    renderInto(context, inventory, elements);
    return inventory;
  }

  void renderInto(
      final InventoryContext context,
      final Inventory inventory,
      final Map<Integer, InventoryElement> elements
  ) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(inventory, "inventory");
    Objects.requireNonNull(elements, "elements");
    inventory.clear();
    for (Map.Entry<Integer, InventoryElement> entry : elements.entrySet()) {
      int slot = entry.getKey();
      InventoryElement element = entry.getValue();
      if (slot < 0 || slot >= inventory.getSize() || element == null) {
        continue;
      }
      ItemStack item = element.render(context);
      inventory.setItem(slot, item == null ? null : item.clone());
    }
  }
}
