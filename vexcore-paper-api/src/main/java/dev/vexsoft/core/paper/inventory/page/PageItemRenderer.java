package dev.vexsoft.core.paper.inventory.page;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;

/**
 * Turns one value from a page source into an inventory element
 */
@FunctionalInterface
public interface PageItemRenderer<T> {

  /** Renders one value at its absolute source index */
  InventoryElement render(InventoryContext context, T item, int absoluteIndex);
}
