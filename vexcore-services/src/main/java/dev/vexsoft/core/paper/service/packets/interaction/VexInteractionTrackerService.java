package dev.vexsoft.core.paper.service.packets.interaction;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Default concurrent tracker for viewer-specific virtual interactions. */
@Dependencies
public final class VexInteractionTrackerService implements InteractionTrackerService {

  private final Map<String, TrackedInteraction> interactions = new ConcurrentHashMap<>();

  /** Creates the tracker through VexCore's service registry. */
  public VexInteractionTrackerService(final VexServiceRegistry services) {
  }

  @Override
  public void track(final TrackedInteraction interaction) {
    interactions.put(key(
        interaction.getHandle().getViewerId(),
        interaction.getHandle().getEntityId()
    ), interaction);
  }

  @Override
  public Optional<TrackedInteraction> find(final UUID viewerId, final int entityId) {
    return Optional.ofNullable(interactions.get(key(viewerId, entityId)));
  }

  @Override
  public Optional<TrackedInteraction> remove(final FakeInteractionHandle handle) {
    return Optional.ofNullable(interactions.remove(key(
        handle.getViewerId(),
        handle.getEntityId()
    )));
  }

  @Override
  public Collection<TrackedInteraction> findOwned(
      final ServiceOwner owner,
      final UUID viewerId
  ) {
    return interactions.values().stream()
        .filter(interaction -> interaction.getHandle().getOwner().equals(owner))
        .filter(interaction -> interaction.getHandle().getViewerId().equals(viewerId))
        .toList();
  }

  @Override
  public Collection<TrackedInteraction> removeOwned(final ServiceOwner owner) {
    Collection<TrackedInteraction> owned = interactions.values().stream()
        .filter(interaction -> interaction.getHandle().getOwner().equals(owner))
        .toList();
    owned.forEach(interaction -> remove(interaction.getHandle()));
    return owned;
  }

  @Override
  public Collection<TrackedInteraction> removeViewer(final UUID viewerId) {
    return removeMatching(viewerId, null);
  }

  @Override
  public Collection<TrackedInteraction> removeViewer(
      final UUID viewerId,
      final DisplayLifecycle lifecycle
  ) {
    return removeMatching(viewerId, lifecycle);
  }

  private Collection<TrackedInteraction> removeMatching(
      final UUID viewerId,
      final DisplayLifecycle lifecycle
  ) {
    Collection<TrackedInteraction> removed = interactions.values().stream()
        .filter(interaction -> interaction.getHandle().getViewerId().equals(viewerId))
        .filter(interaction -> lifecycle == null || interaction.getLifecycle().contains(lifecycle))
        .toList();
    removed.forEach(interaction -> remove(interaction.getHandle()));
    return removed;
  }

  private static String key(final UUID viewerId, final int entityId) {
    return viewerId + ":" + entityId;
  }
}
