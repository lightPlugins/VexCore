package dev.vexsoft.core.inventory.page;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.List;

/**
 * Supplies the items rendered inside a paged inventory for its current viewer
 */
@FunctionalInterface
public interface PageSource<T> {

  /** Returns the items available to the current viewer */
  public List<T> getItems(InventoryContext context);
}
