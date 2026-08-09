package dev.vexsoft.core.paper.inventory.page;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import java.util.List;

/**
 * Supplies the items rendered inside a paged inventory for its current viewer
 */
@FunctionalInterface
public interface PageSource<T> {

  /** Returns the items available to the current viewer */
  List<T> getItems(InventoryContext context);
}
