package dev.vexsoft.core.paper.packet;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.version.MinecraftVersion;
import dev.vexsoft.core.packets.version.PacketCapability;
import dev.vexsoft.core.packets.version.PacketComponent;
import dev.vexsoft.core.packets.version.PacketVersionDefinition;
import dev.vexsoft.core.packets.version.PacketVersionService;
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
