package dev.vexsoft.core.paper.service.items;

import dev.vexsoft.core.paper.items.internal.VexComponentPatch;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.inventory.ItemStack;

/**
 * Applies stable Vex components through the active Minecraft implementation
 */
public interface ItemComponentAdapterService extends VexService {

  /** Applies every physical operation in a component patch to an item stack */
  void apply(ItemStack itemStack, VexComponentPatch patch);

  /** Removes name and lore components that must only exist in outgoing packets */
  void clearPresentation(ItemStack itemStack);
}
