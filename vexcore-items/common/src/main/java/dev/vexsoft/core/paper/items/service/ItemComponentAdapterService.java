package dev.vexsoft.core.paper.items.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.items.VexArmorTrim;
import dev.vexsoft.core.paper.items.VexAdventureBlocks;
import dev.vexsoft.core.paper.items.VexMiningTool;
import dev.vexsoft.core.paper.items.internal.VexComponentPatch;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Applies stable Vex components through the active Minecraft implementation */
public interface ItemComponentAdapterService extends VexService {

    /** Applies every physical operation in a component patch to an item stack */
    void apply(ItemStack itemStack, VexComponentPatch patch);

    /** Removes name and lore components that must only exist in outgoing packets */
    void clearPresentation(ItemStack itemStack);

    /** Updates Adventure breaking without rebuilding the item; returns whether physical data changed. */
    boolean synchronizeAdventureBreaks(ItemStack itemStack, VexAdventureBlocks blocks);

    /** Updates stable native TOOL rules; returns whether physical item data changed. */
    boolean synchronizeMiningTool(ItemStack itemStack, VexMiningTool tool);

    /** Resolves and validates trim IDs against the running server's registries. */
    VexArmorTrim armorTrim(NamespacedKey pattern, NamespacedKey material);
}
