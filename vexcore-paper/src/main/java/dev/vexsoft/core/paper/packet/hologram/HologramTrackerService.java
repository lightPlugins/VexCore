package dev.vexsoft.core.paper.packet.hologram;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.hologram.InteractableHologramHandle;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import dev.vexsoft.core.packets.display.DisplayLifecycle;

/**
 * Shares active holograms with the central packet connection handler
 */
public interface HologramTrackerService extends VexService {

  /** Stores a hologram for interaction routing and cleanup */
  void track(TrackedHologram hologram);

  /** Finds a hologram by its viewer and interaction entity id */
  Optional<TrackedHologram> find(UUID viewerId, int interactionEntityId);

  /** Removes and returns a hologram by its public handle */
  Optional<TrackedHologram> remove(InteractableHologramHandle handle);

  /** Returns all holograms owned by one plugin for one viewer */
  Collection<TrackedHologram> findOwned(ServiceOwner owner, UUID viewerId);

  /** Removes all holograms owned by one plugin */
  Collection<TrackedHologram> removeOwned(ServiceOwner owner);

  /** Removes every hologram associated with one viewer */
  Collection<TrackedHologram> removeViewer(UUID viewerId);

  /** Removes holograms configured for a viewer lifecycle event */
  Collection<TrackedHologram> removeViewer(UUID viewerId, DisplayLifecycle lifecycle);
}
