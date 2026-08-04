package dev.vexsoft.core.paper.packet;

import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.version.MinecraftVersion;
import dev.vexsoft.core.packets.version.PacketVersionDefinition;
import dev.vexsoft.core.packets.version.PacketVersionRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VexPacketVersionRegistry implements PacketVersionRegistry {

  private final VexServiceRegistry services;
  private final Map<MinecraftVersion, PacketVersionDefinition> definitions = new LinkedHashMap<>();

  public VexPacketVersionRegistry(final VexServiceRegistry services) {
    this.services = services;
  }

  @Override
  public void register(final Class<? extends PacketVersionDefinition> definitionType) {
    PacketVersionDefinition definition = VexClassFactory.create(
        definitionType,
        services,
        "Packet version definition"
    );
    for (MinecraftVersion version : definition.getSupportedVersions()) {
      PacketVersionDefinition existing = definitions.putIfAbsent(version, definition);
      if (existing != null) {
        throw new IllegalStateException("Packet version is already registered: " + version);
      }
    }
  }

  @Override
  public PacketVersionDefinition require(final MinecraftVersion minecraftVersion) {
    PacketVersionDefinition definition = definitions.get(minecraftVersion);
    if (definition == null) {
      throw new IllegalStateException(
          "VexCore does not support Minecraft " + minecraftVersion
              + " packets. Supported versions: " + getSupportedVersions()
      );
    }
    return definition;
  }

  @Override
  public Collection<MinecraftVersion> getSupportedVersions() {
    return List.copyOf(definitions.keySet());
  }
}
