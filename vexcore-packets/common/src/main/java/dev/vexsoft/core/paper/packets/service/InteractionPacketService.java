package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionRequest;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Manages viewer-specific interaction hitboxes without spawning server entities. */
public interface InteractionPacketService extends VexService {

  /**
   * Spawns an interaction entity visible and usable only by the given viewer.
   *
   * @param viewer player receiving and interacting with the virtual entity
   * @param request hitbox, lifecycle and callback properties
   * @return owner- and viewer-bound interaction handle
   */
  FakeInteractionHandle spawn(Player viewer, FakeInteractionRequest request);

  /**
   * Changes the dimensions of a tracked interaction hitbox.
   *
   * @param handle tracked interaction identity
   * @param width positive finite hitbox width
   * @param height positive finite hitbox height
   */
  void updateHitbox(FakeInteractionHandle handle, float width, float height);

  /**
   * Teleports a tracked interaction entity for its viewer.
   *
   * @param handle tracked interaction identity
   * @param location destination in the viewer's current world
   */
  void teleport(FakeInteractionHandle handle, Location location);

  /**
   * Removes a tracked interaction entity from its viewer.
   *
   * @param handle tracked interaction identity
   */
  void remove(FakeInteractionHandle handle);

  /**
   * Removes every interaction entity owned by this service for the viewer.
   *
   * @param viewer player whose owned interaction entities should be removed
   */
  void removeAll(Player viewer);
}
