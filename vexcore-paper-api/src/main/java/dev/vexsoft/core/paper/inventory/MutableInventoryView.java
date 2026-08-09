package dev.vexsoft.core.paper.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Allows a view to manage clicks and item movement without automatic cancellation
 */
public interface MutableInventoryView extends InventoryView {

  /** Handles a click anywhere inside this inventory view */
  default void onInventoryClick(InventoryContext context, InventoryClickEvent event) { }

  /** Handles dragged items inside this inventory view */
  default void onInventoryDrag(InventoryContext context, InventoryDragEvent event) { }
}
