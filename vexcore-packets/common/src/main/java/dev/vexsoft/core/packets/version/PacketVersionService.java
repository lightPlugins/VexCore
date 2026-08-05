package dev.vexsoft.core.packets.version;

import dev.vexsoft.core.api.service.VexService;
import java.util.Map;
import java.util.Set;

/**
 * Exposes the selected packet adapter and its supported capabilities
 */
public interface PacketVersionService extends VexService {

  /** Returns the Minecraft version reported by the running server */
  MinecraftVersion getMinecraftVersion();

  /** Returns the base revision of the selected packet definition */
  MinecraftVersion getAdapterVersion();

  /** Returns the adapter revision used by each packet component */
  Map<PacketComponent, MinecraftVersion> getComponentVersions();

  /** Returns every packet capability provided by the selected definition */
  Set<PacketCapability> getCapabilities();

  /** Checks whether the selected definition provides a capability */
  boolean supports(PacketCapability capability);
}
