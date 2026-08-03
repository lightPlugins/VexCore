package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryKey;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Filters the values displayed by a controlled paged inventory
 */
public interface PageFilterControl<T> extends PageControl {

  /** Returns the predicate applied for the selected mode and viewer */
  public Predicate<T> getPredicate(String modeId, InventoryKey inventoryKey, UUID viewerId);
}
