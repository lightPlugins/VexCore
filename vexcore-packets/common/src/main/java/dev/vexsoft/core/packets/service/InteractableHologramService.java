package dev.vexsoft.core.packets.service;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.packets.hologram.InteractableHologramHandle;
import dev.vexsoft.core.packets.hologram.InteractableHologramRequest;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Manages viewer-specific text displays with interactive hitboxes
 */
public interface InteractableHologramService extends VexService {

  /** Spawns an interactable hologram for the given viewer */
  public InteractableHologramHandle spawn(Player viewer, InteractableHologramRequest request);

  /** Updates the text display attached to a tracked hologram */
  public void update(InteractableHologramHandle handle, FakeTextDisplayUpdate update);

  /** Changes the dimensions of the hologram interaction hitbox */
  public void updateHitbox(InteractableHologramHandle handle, float width, float height);

  /** Changes the position of the hologram interaction hitbox */
  public void updateHitboxOffset(InteractableHologramHandle handle, Vector offset);

  /** Changes the position of the hologram interaction hitbox */
  public default void updateHitboxOffset(
      final InteractableHologramHandle handle,
      final double x,
      final double y,
      final double z
  ) {
    updateHitboxOffset(handle, new Vector(x, y, z));
  }

  /** Teleports the display and interaction hitbox together */
  public void teleport(InteractableHologramHandle handle, Location location);

  /** Removes a tracked hologram from its viewer */
  public void remove(InteractableHologramHandle handle);

  /** Removes every hologram owned by this service for the viewer */
  public void removeAll(Player viewer);
}
