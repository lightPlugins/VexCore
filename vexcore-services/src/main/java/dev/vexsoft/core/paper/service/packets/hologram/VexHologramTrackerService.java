package dev.vexsoft.core.paper.service.packets.hologram;


import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramHandle;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;

@Dependencies
public final class VexHologramTrackerService implements HologramTrackerService {

  private final Map<String, TrackedHologram> holograms = new ConcurrentHashMap<>();

  public VexHologramTrackerService(final VexServiceRegistry services) {
  }

  @Override
  public void track(final TrackedHologram hologram) {
    holograms.put(key(
        hologram.getHandle().getViewerId(),
        hologram.getHandle().getInteractionEntityId()
    ), hologram);
  }

  @Override
  public Optional<TrackedHologram> find(final UUID viewerId, final int interactionEntityId) {
    return Optional.ofNullable(holograms.get(key(viewerId, interactionEntityId)));
  }

  @Override
  public Optional<TrackedHologram> remove(final InteractableHologramHandle handle) {
    return Optional.ofNullable(holograms.remove(key(
        handle.getViewerId(),
        handle.getInteractionEntityId()
    )));
  }

  @Override
  public Collection<TrackedHologram> findOwned(
      final ServiceOwner owner,
      final UUID viewerId
  ) {
    return holograms.values().stream()
        .filter(hologram -> hologram.getHandle().getOwner().equals(owner))
        .filter(hologram -> hologram.getHandle().getViewerId().equals(viewerId))
        .toList();
  }

  @Override
  public Collection<TrackedHologram> removeOwned(final ServiceOwner owner) {
    Collection<TrackedHologram> owned = holograms.values().stream()
        .filter(hologram -> hologram.getHandle().getOwner().equals(owner))
        .toList();
    owned.forEach(hologram -> remove(hologram.getHandle()));
    return owned;
  }

  @Override
  public Collection<TrackedHologram> removeViewer(final UUID viewerId) {
    return removeMatching(viewerId, null);
  }

  @Override
  public Collection<TrackedHologram> removeViewer(
      final UUID viewerId,
      final DisplayLifecycle lifecycle
  ) {
    return removeMatching(viewerId, lifecycle);
  }

  private Collection<TrackedHologram> removeMatching(
      final UUID viewerId,
      final DisplayLifecycle lifecycle
  ) {
    Collection<TrackedHologram> removed = holograms.values().stream()
        .filter(hologram -> hologram.getHandle().getViewerId().equals(viewerId))
        .filter(hologram -> lifecycle == null || hologram.getLifecycle().contains(lifecycle))
        .toList();
    removed.forEach(hologram -> remove(hologram.getHandle()));
    return removed;
  }

  private static String key(final UUID viewerId, final int interactionEntityId) {
    return viewerId + ":" + interactionEntityId;
  }
}
