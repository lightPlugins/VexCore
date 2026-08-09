package dev.vexsoft.core.paper.items;

import org.bukkit.inventory.ItemStack;

/**
 * Builds Vex item stacks through stable component definitions
 */
public interface ItemStackBuilder {

  /** Sets a valued component on the resulting item or its packet presentation */
  <T> ItemStackBuilder setData(VexComponentData<T> component, T value);

  /** Enables a component that carries no value */
  ItemStackBuilder setData(VexFlagComponentData component);

  /** Removes a component including values inherited from the item prototype */
  ItemStackBuilder unsetData(VexComponentData<?> component);

  /** Restores a component to the item prototype value */
  ItemStackBuilder resetData(VexComponentData<?> component);

  /** Creates a new item stack without modifying the builder source */
  ItemStack build();
}
