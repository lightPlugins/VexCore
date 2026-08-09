package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramHandle;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramRequest;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Manages viewer-specific text displays with interactive hitboxes
 */
public interface InteractableHologramService extends VexService {

  /** Spawns an interactable hologram for the given viewer */
  InteractableHologramHandle spawn(Player viewer, InteractableHologramRequest request);

  /** Updates the text display attached to a tracked hologram */
  void update(InteractableHologramHandle handle, FakeTextDisplayUpdate update);

  /** Changes the dimensions of the hologram interaction hitbox */
  void updateHitbox(InteractableHologramHandle handle, float width, float height);

  /** Changes the position of the hologram interaction hitbox */
  void updateHitboxOffset(InteractableHologramHandle handle, Vector offset);

  /** Changes the position of the hologram interaction hitbox */
  default void updateHitboxOffset(
      final InteractableHologramHandle handle,
      final double x,
      final double y,
      final double z
  ) {
    updateHitboxOffset(handle, new Vector(x, y, z));
  }

  /** Teleports the display and interaction hitbox together */
  void teleport(InteractableHologramHandle handle, Location location);

  /** Removes a tracked hologram from its viewer */
  void remove(InteractableHologramHandle handle);

  /** Removes every hologram owned by this service for the viewer */
  void removeAll(Player viewer);
}
