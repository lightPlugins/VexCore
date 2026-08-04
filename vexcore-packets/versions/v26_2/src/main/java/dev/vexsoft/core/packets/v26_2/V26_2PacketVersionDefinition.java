package dev.vexsoft.core.packets.v26_2;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.DisplayPacketAdapterService;
import dev.vexsoft.core.packets.internal.EntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.internal.HologramInteractionAdapterService;
import dev.vexsoft.core.packets.internal.ItemMetaPacketAdapterService;
import dev.vexsoft.core.packets.internal.PacketConnectionAdapterService;
import dev.vexsoft.core.packets.internal.PacketTransportAdapterService;
import dev.vexsoft.core.packets.v26_2.display.VexDisplayPacketAdapterService;
import dev.vexsoft.core.packets.v26_2.effect.VexEntityEffectPacketAdapterService;
import dev.vexsoft.core.packets.v26_2.item.VexItemMetaPacketAdapterService;
import dev.vexsoft.core.packets.version.MinecraftVersion;
import dev.vexsoft.core.packets.version.PacketCapability;
import dev.vexsoft.core.packets.version.PacketComponent;
import dev.vexsoft.core.packets.version.PacketVersionDefinition;
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
