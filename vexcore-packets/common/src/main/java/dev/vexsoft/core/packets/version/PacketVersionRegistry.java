package dev.vexsoft.core.packets.version;

import java.util.Collection;

/**
 * Registers packet definitions and selects one for the running Minecraft version
 */
public interface PacketVersionRegistry {

  /** Creates and registers an annotated packet version definition */
  public void register(Class<? extends PacketVersionDefinition> definitionType);

  /** Selects the definition that explicitly supports the given version */
  public PacketVersionDefinition require(MinecraftVersion minecraftVersion);

  /** Returns every Minecraft version explicitly supported by registered definitions */
  public Collection<MinecraftVersion> getSupportedVersions();
}
