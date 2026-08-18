package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Manages viewer-specific block displays without spawning server entities. */
public interface BlockDisplayPacketService extends VexService {

  /**
   * Spawns a block display visible only to the given viewer.
   *
   * @param viewer player receiving the virtual entity packets
   * @param request immutable display properties
   * @return owner- and viewer-bound display handle
   */
  FakeDisplayHandle spawn(Player viewer, FakeBlockDisplayRequest request);

  /**
   * Applies the supplied properties to a tracked block display.
   *
   * @param handle display identity returned by {@link #spawn(Player, FakeBlockDisplayRequest)}
   * @param update partial display update
   */
  void update(FakeDisplayHandle handle, FakeBlockDisplayUpdate update);

  /**
   * Teleports a tracked block display for its viewer.
   *
   * @param handle tracked display identity
   * @param location destination in the viewer's current world
   */
  void teleport(FakeDisplayHandle handle, Location location);

  /**
   * Removes a tracked block display from its viewer.
   *
   * @param handle tracked display identity
   */
  void remove(FakeDisplayHandle handle);

  /**
   * Removes every block display owned by this service for the viewer.
   *
   * @param viewer player whose owned displays should be removed
   */
  void removeAll(Player viewer);
}
