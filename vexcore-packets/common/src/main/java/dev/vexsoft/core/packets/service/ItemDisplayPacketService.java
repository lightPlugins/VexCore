package dev.vexsoft.core.packets.service;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.packets.display.FakeItemDisplayUpdate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages viewer-specific item displays without spawning server entities
 */
public interface ItemDisplayPacketService extends VexService {

  /** Spawns an item display visible only to the given viewer */
  public FakeDisplayHandle spawn(Player viewer, FakeItemDisplayRequest request);

  /** Applies the supplied properties to a tracked item display */
  public void update(FakeDisplayHandle handle, FakeItemDisplayUpdate update);

  /** Teleports a tracked item display for its viewer */
  public void teleport(FakeDisplayHandle handle, Location location);

  /** Removes a tracked item display from its viewer */
  public void remove(FakeDisplayHandle handle);

  /** Removes every item display owned by this service for the viewer */
  public void removeAll(Player viewer);
}
