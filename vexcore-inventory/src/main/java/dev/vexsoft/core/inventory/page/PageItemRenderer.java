package dev.vexsoft.core.inventory.page;

import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryElement;

/**
 * Turns one value from a page source into an inventory element
 */
@FunctionalInterface
public interface PageItemRenderer<T> {

  /** Renders one value at its absolute source index */
  InventoryElement render(InventoryContext context, T item, int absoluteIndex);
}
