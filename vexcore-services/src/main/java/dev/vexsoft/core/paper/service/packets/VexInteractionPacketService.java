package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionRequest;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.InteractionPacketService;
import dev.vexsoft.core.paper.service.packets.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.service.packets.interaction.TrackedInteraction;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Default owner-scoped service for viewer-specific virtual interaction entities. */
@Dependencies({DisplayPacketAdapterService.class, InteractionTrackerService.class})
public final class VexInteractionPacketService
    implements InteractionPacketService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final InteractionTrackerService tracker;

  /** Creates the interaction service through VexCore's service registry. */
  public VexInteractionPacketService(final VexServiceRegistry services) {
    owner = services.getOwner();
    adapter = services.require(DisplayPacketAdapterService.class);
    tracker = services.require(InteractionTrackerService.class);
  }

  @Override
  public FakeInteractionHandle spawn(
      final Player viewer,
      final FakeInteractionRequest request
  ) {
    FakeInteractionHandle handle = new FakeInteractionHandle(
        owner,
        viewer.getUniqueId(),
        adapter.allocateEntityId(),
        UUID.randomUUID()
    );
    adapter.spawnInteraction(
        viewer,
        handle.getEntityId(),
        handle.getEntityUuid(),
        request.getLocation(),
        request.getWidth(),
        request.getHeight()
    );
    tracker.track(new TrackedInteraction(
        handle,
        request.getInteractHandler(),
        request.getLifecycle()
    ));
    return handle;
  }

  @Override
  public void updateHitbox(
      final FakeInteractionHandle handle,
      final float width,
      final float height
  ) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
      throw new IllegalArgumentException("interaction dimensions must be finite and positive");
    }
    tracked(handle).ifPresent(interaction -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.updateInteraction(viewer, handle.getEntityId(), width, height);
      }
    });
  }

  @Override
  public void teleport(final FakeInteractionHandle handle, final Location location) {
    tracked(handle).ifPresent(interaction -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.teleport(viewer, handle.getEntityId(), location);
      }
    });
  }

  @Override
  public void remove(final FakeInteractionHandle handle) {
    requireOwner(handle);
    tracker.remove(handle).ifPresent(interaction -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.remove(viewer, handle.getEntityId());
      }
    });
  }

  @Override
  public void removeAll(final Player viewer) {
    tracker.findOwned(owner, viewer.getUniqueId()).stream()
        .map(TrackedInteraction::getHandle)
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    tracker.removeOwned(owner).forEach(interaction -> {
      Player viewer = Bukkit.getPlayer(interaction.getHandle().getViewerId());
      if (viewer != null) {
        adapter.remove(viewer, interaction.getHandle().getEntityId());
      }
    });
  }

  private Optional<TrackedInteraction> tracked(
      final FakeInteractionHandle handle
  ) {
    requireOwner(handle);
    return tracker.find(handle.getViewerId(), handle.getEntityId());
  }

  private void requireOwner(final FakeInteractionHandle handle) {
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Interaction handle belongs to another plugin");
    }
  }
}
