package dev.vexsoft.core.packets.service;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.packets.display.FakeTextDisplayUpdate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages viewer-specific text displays without spawning server entities
 */
public interface TextDisplayPacketService extends VexService {

  /** Spawns a text display visible only to the given viewer */
  public FakeDisplayHandle spawn(Player viewer, FakeTextDisplayRequest request);

  /** Applies the supplied properties to a tracked text display */
  public void update(FakeDisplayHandle handle, FakeTextDisplayUpdate update);

  /** Teleports a tracked text display for its viewer */
  public void teleport(FakeDisplayHandle handle, Location location);

  /** Removes a tracked text display from its viewer */
  public void remove(FakeDisplayHandle handle);

  /** Removes every text display owned by this service for the viewer */
  public void removeAll(Player viewer);
}
