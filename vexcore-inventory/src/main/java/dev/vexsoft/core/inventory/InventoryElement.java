package dev.vexsoft.core.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Renders one inventory slot and optionally handles clicks on it
 */
public interface InventoryElement {

  /** Creates the item shown to the current viewer */
  public ItemStack render(InventoryContext context);

  /** Handles a click on this element */
  public default void onClick(InventoryContext context, InventoryClickEvent event) { }

  /** Checks whether this element accepts click actions */
  public default boolean isClickable() {
    return true;
  }
}
