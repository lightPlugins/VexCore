package dev.vexsoft.core.inventory;

/**
 * Creates a fresh inventory view for a registered inventory key
 */
public interface InventoryDefinition {

  /** Returns the unique key used to open this inventory */
  public InventoryKey getKey();

  /** Creates a new view for the given viewer context */
  public InventoryView create(InventoryContext context);
}
