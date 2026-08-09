package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages viewer-specific text displays without spawning server entities
 */
public interface TextDisplayPacketService extends VexService {

  /** Spawns a text display visible only to the given viewer */
  FakeDisplayHandle spawn(Player viewer, FakeTextDisplayRequest request);

  /** Applies the supplied properties to a tracked text display */
  void update(FakeDisplayHandle handle, FakeTextDisplayUpdate update);

  /** Teleports a tracked text display for its viewer */
  void teleport(FakeDisplayHandle handle, Location location);

  /** Removes a tracked text display from its viewer */
  void remove(FakeDisplayHandle handle);

  /** Removes every text display owned by this service for the viewer */
  void removeAll(Player viewer);
}
