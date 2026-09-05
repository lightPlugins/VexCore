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
import java.util.Set;

/** Default concurrent tracker for viewer-specific virtual interactions. */
@Dependencies
public final class VexInteractionTrackerService implements InteractionTrackerService {

  private final Map<String, TrackedInteraction> interactions = new ConcurrentHashMap<>();
  private final Map<UUID, Set<ServiceOwner>> inputBlocks = new ConcurrentHashMap<>();

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
  public boolean setInputBlocked(
      final ServiceOwner owner,
      final UUID viewerId,
      final boolean blocked
  ) {
    if (blocked) {
      inputBlocks.computeIfAbsent(viewerId, ignored -> ConcurrentHashMap.newKeySet()).add(owner);
    } else {
      inputBlocks.computeIfPresent(viewerId, (ignored, owners) -> {
        owners.remove(owner);
        return owners.isEmpty() ? null : owners;
      });
    }
    return isInputBlocked(viewerId);
  }

  @Override
  public boolean isInputBlocked(final UUID viewerId) {
    Set<ServiceOwner> owners = inputBlocks.get(viewerId);
    return owners != null && !owners.isEmpty();
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
  public void clearInputBlocks(final ServiceOwner owner) {
    inputBlocks.forEach((viewerId, owners) -> {
      owners.remove(owner);
      if (owners.isEmpty()) {
        inputBlocks.remove(viewerId, owners);
      }
    });
  }

  @Override
  public Collection<TrackedInteraction> removeViewer(final UUID viewerId) {
    inputBlocks.remove(viewerId);
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
