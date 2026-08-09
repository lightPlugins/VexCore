package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.paper.packet.PacketVersions;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.version.MinecraftVersion;
import dev.vexsoft.core.paper.packets.version.PacketCapability;
import dev.vexsoft.core.paper.packets.version.PacketComponent;
import dev.vexsoft.core.paper.packets.version.PacketVersionDefinition;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;

@Dependencies
public final class VexPacketVersionService implements PacketVersionService {

  private final MinecraftVersion minecraftVersion;
  private final PacketVersionDefinition definition;

  public VexPacketVersionService(final VexServiceRegistry services) {
    this.minecraftVersion = MinecraftVersion.of(Bukkit.getMinecraftVersion());
    this.definition = PacketVersions.select(services);
  }

  @Override
  public MinecraftVersion getMinecraftVersion() {
    return minecraftVersion;
  }

  @Override
  public MinecraftVersion getAdapterVersion() {
    return definition.getAdapterVersion();
  }

  @Override
  public Map<PacketComponent, MinecraftVersion> getComponentVersions() {
    return definition.getComponentVersions();
  }

  @Override
  public Set<PacketCapability> getCapabilities() {
    return definition.getCapabilities();
  }

  @Override
  public boolean supports(final PacketCapability capability) {
    return definition.getCapabilities().contains(capability);
  }
}
