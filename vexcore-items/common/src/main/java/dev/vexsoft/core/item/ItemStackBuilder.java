package dev.vexsoft.core.item;

import org.bukkit.inventory.ItemStack;

/**
 * Builds Vex item stacks through stable component definitions
 */
public interface ItemStackBuilder {

  /** Sets a valued component on the resulting item or its packet presentation */
  public <T> ItemStackBuilder setData(VexComponentData<T> component, T value);

  /** Enables a component that carries no value */
  public ItemStackBuilder setData(VexFlagComponentData component);

  /** Removes a component including values inherited from the item prototype */
  public ItemStackBuilder unsetData(VexComponentData<?> component);

  /** Restores a component to the item prototype value */
  public ItemStackBuilder resetData(VexComponentData<?> component);

  /** Creates a new item stack without modifying the builder source */
  public ItemStack build();
}
