package dev.vexsoft.core.inventory;

/**
 * Creates a fresh inventory view for a registered inventory key
 */
public interface InventoryDefinition {

  /** Returns the unique key used to open this inventory */
  InventoryKey getKey();

  /** Creates a new view for the given viewer context */
  InventoryView create(InventoryContext context);
}
