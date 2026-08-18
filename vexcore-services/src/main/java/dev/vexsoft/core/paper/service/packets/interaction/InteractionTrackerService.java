package dev.vexsoft.core.paper.service.packets.interaction;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Shares virtual interactions with the central packet connection handler. */
public interface InteractionTrackerService extends VexService {

  /** Stores a virtual interaction for routing and cleanup. */
  void track(TrackedInteraction interaction);

  /** Finds an interaction by its viewer and virtual entity ID. */
  Optional<TrackedInteraction> find(UUID viewerId, int entityId);

  /** Removes and returns an interaction by its public handle. */
  Optional<TrackedInteraction> remove(FakeInteractionHandle handle);

  /** Returns all interactions owned by one plugin for one viewer. */
  Collection<TrackedInteraction> findOwned(ServiceOwner owner, UUID viewerId);

  /** Removes all interactions owned by one plugin. */
  Collection<TrackedInteraction> removeOwned(ServiceOwner owner);

  /** Removes every interaction associated with one viewer. */
  Collection<TrackedInteraction> removeViewer(UUID viewerId);

  /** Removes viewer interactions configured for the supplied lifecycle event. */
  Collection<TrackedInteraction> removeViewer(UUID viewerId, DisplayLifecycle lifecycle);
}
