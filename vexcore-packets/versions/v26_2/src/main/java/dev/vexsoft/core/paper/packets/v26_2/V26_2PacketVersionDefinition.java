package dev.vexsoft.core.paper.packets.v26_2;

import dev.vexsoft.core.paper.service.packets.v26_2.VexHologramInteractionAdapterService;
import dev.vexsoft.core.paper.service.packets.v26_2.VexPacketConnectionAdapterService;
import dev.vexsoft.core.paper.service.packets.v26_2.VexPacketTransportAdapterService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.EntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import dev.vexsoft.core.paper.service.packets.v26_2.VexDisplayPacketAdapterService;
import dev.vexsoft.core.paper.service.packets.v26_2.VexEntityEffectPacketAdapterService;
import dev.vexsoft.core.paper.service.packets.v26_2.VexItemMetaPacketAdapterService;
import dev.vexsoft.core.paper.packets.version.MinecraftVersion;
import dev.vexsoft.core.paper.packets.version.PacketCapability;
import dev.vexsoft.core.paper.packets.version.PacketComponent;
import dev.vexsoft.core.paper.packets.version.PacketVersionDefinition;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Dependencies
public class V26_2PacketVersionDefinition implements PacketVersionDefinition {

  private static final MinecraftVersion VERSION = MinecraftVersion.of("26.2");

  public V26_2PacketVersionDefinition(final VexServiceRegistry services) {
  }

  @Override
  public MinecraftVersion getAdapterVersion() {
    return VERSION;
  }

  @Override
  public Set<MinecraftVersion> getSupportedVersions() {
    return Set.of(VERSION);
  }

  @Override
  public Set<PacketCapability> getCapabilities() {
    return Set.copyOf(EnumSet.allOf(PacketCapability.class));
  }

  @Override
  public Map<PacketComponent, MinecraftVersion> getComponentVersions() {
    Map<PacketComponent, MinecraftVersion> versions = new EnumMap<>(PacketComponent.class);
    for (PacketComponent component : PacketComponent.values()) {
      versions.put(component, VERSION);
    }
    return Map.copyOf(versions);
  }

  @Override
  public Class<? extends PacketTransportAdapterService> getTransportAdapter() {
    return VexPacketTransportAdapterService.class;
  }

  @Override
  public Class<? extends DisplayPacketAdapterService> getDisplayAdapter() {
    return VexDisplayPacketAdapterService.class;
  }

  @Override
  public Class<? extends EntityEffectPacketAdapterService> getEntityEffectAdapter() {
    return VexEntityEffectPacketAdapterService.class;
  }

  @Override
  public Class<? extends PacketConnectionAdapterService> getConnectionAdapter() {
    return VexPacketConnectionAdapterService.class;
  }

  @Override
  public Class<? extends ItemMetaPacketAdapterService> getItemMetaAdapter() {
    return VexItemMetaPacketAdapterService.class;
  }

  @Override
  public Class<? extends HologramInteractionAdapterService> getHologramInteractionAdapter() {
    return VexHologramInteractionAdapterService.class;
  }
}
