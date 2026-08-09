package dev.vexsoft.core.paper.packet;

import dev.vexsoft.core.paper.service.packets.VexPacketVersionRegistry;
import dev.vexsoft.core.paper.packets.version.MinecraftVersion;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.v26_2.V26_2PacketVersionDefinition;
import dev.vexsoft.core.paper.packets.version.PacketVersionDefinition;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;

@UtilityClass
public class PacketVersions {

  public static PacketVersionDefinition select(final VexServiceRegistry services) {
    VexPacketVersionRegistry versions = new VexPacketVersionRegistry(services);
    versions.register(V26_2PacketVersionDefinition.class);
    return versions.require(
        MinecraftVersion.of(Bukkit.getMinecraftVersion())
    );
  }
}
