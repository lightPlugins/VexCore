package dev.vexsoft.core.paper.packets.version;

import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
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
}
