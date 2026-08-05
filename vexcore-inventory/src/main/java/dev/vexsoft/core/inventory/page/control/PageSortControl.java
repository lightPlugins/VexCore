package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryKey;
import java.util.Comparator;
import java.util.UUID;

/**
 * Sorts the values displayed by a controlled paged inventory
 */
public interface PageSortControl<T> extends PageControl {

  /** Returns the comparator applied for the selected mode and viewer */
  Comparator<T> getComparator(String modeId, InventoryKey inventoryKey, UUID viewerId);
}
