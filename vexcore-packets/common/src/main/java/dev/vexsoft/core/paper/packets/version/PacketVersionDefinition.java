package dev.vexsoft.core.paper.packets.version;

import dev.vexsoft.core.paper.packets.service.block.VirtualBlockPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.connection.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.connection.PacketTransportAdapterService;
import dev.vexsoft.core.paper.packets.service.display.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.effect.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.interaction.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.interactiveui.InteractiveUiPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.item.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.item.RecipeBookPacketAdapterService;
import java.util.Map;
import java.util.Set;

/**
 * Selects the packet component implementations used for compatible Minecraft versions
 */
public interface PacketVersionDefinition {

    /** Returns the base Minecraft revision represented by this definition */
    MinecraftVersion getAdapterVersion();

    /** Returns every Minecraft version explicitly compatible with this definition */
    Set<MinecraftVersion> getSupportedVersions();

    /** Returns every packet capability provided by this definition */
    Set<PacketCapability> getCapabilities();

    /** Returns the revision used by each selected packet component */
    Map<PacketComponent, MinecraftVersion> getComponentVersions();

    /** Returns the selected native packet transport implementation */
    Class<? extends PacketTransportAdapterService> getTransportAdapter();

    /** Returns the selected display packet implementation */
    Class<? extends DisplayPacketAdapterService> getDisplayAdapter();

    /** Returns the selected entity effect packet implementation */
    Class<? extends EntityEffectPacketAdapterService> getEntityEffectAdapter();

    /** Returns the selected player connection implementation */
    Class<? extends PacketConnectionAdapterService> getConnectionAdapter();

    /** Returns the selected item metadata packet implementation */
    Class<? extends ItemMetaPacketAdapterService> getItemMetaAdapter();

    /** Returns the selected hologram interaction decoder */
    Class<? extends HologramInteractionAdapterService> getHologramInteractionAdapter();

    /** Returns the selected interactive canvas camera and input adapter. */
    Class<? extends InteractiveUiPacketAdapterService> getInteractiveUiAdapter();
    /** Returns the adapter for recipe-book visibility synchronization. */
    Class<? extends RecipeBookPacketAdapterService> getRecipeBookAdapter();

    /** Returns the adapter for virtual blocks and prediction acknowledgements. */
    Class<? extends VirtualBlockPacketAdapterService> getVirtualBlockAdapter();
}
