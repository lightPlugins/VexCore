package dev.vexsoft.core.paper.inventory;

import java.util.Map;
import net.kyori.adventure.text.Component;

/**
 * Describes the title, size and elements rendered for an inventory
 */
public interface InventoryView {

  /** Returns the key represented by this view */
  InventoryKey getKey();

  /** Returns the number of slots rendered by this view */
  int getSize();

  /** Creates the title shown to the current viewer */
  Component getTitle(InventoryContext context);

  /** Creates the slot elements shown to the current viewer */
  Map<Integer, InventoryElement> getElements(InventoryContext context);

  /** Runs after this view has been opened */
  default void onOpen(InventoryContext context) { }

  /** Runs after this view has been closed or replaced */
  default void onClose(InventoryContext context) { }
}
