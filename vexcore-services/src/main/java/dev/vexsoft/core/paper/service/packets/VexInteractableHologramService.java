package dev.vexsoft.core.paper.service.packets;

import java.util.Optional;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramHandle;
import dev.vexsoft.core.paper.packets.hologram.InteractableHologramRequest;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.InteractableHologramService;
import dev.vexsoft.core.paper.service.packets.hologram.HologramTrackerService;
import dev.vexsoft.core.paper.service.packets.hologram.TrackedHologram;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

@Dependencies({DisplayPacketAdapterService.class, HologramTrackerService.class})
public final class VexInteractableHologramService
    implements InteractableHologramService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final HologramTrackerService tracker;

  public VexInteractableHologramService(final VexServiceRegistry services) {
    this.owner = services.getOwner();
    this.adapter = services.require(DisplayPacketAdapterService.class);
    this.tracker = services.require(HologramTrackerService.class);
  }

  @Override
  public InteractableHologramHandle spawn(
      final Player viewer,
      final InteractableHologramRequest request
  ) {
    FakeTextDisplayRequest text = request.getTextDisplayRequest();
    int textId = adapter.allocateEntityId();
    int interactionId = adapter.allocateEntityId();
    UUID textUuid = UUID.randomUUID();
    UUID interactionUuid = UUID.randomUUID();
    FakeDisplayHandle textHandle = new FakeDisplayHandle(
        owner, viewer.getUniqueId(), textId, textUuid, FakeDisplayKind.TEXT
    );
    InteractableHologramHandle handle = new InteractableHologramHandle(
        owner, viewer.getUniqueId(), textId, textUuid, interactionId, interactionUuid
    );
    adapter.spawnText(viewer, textHandle, text);
    adapter.spawnInteraction(
        viewer, interactionId, interactionUuid,
        interactionLocation(text.getLocation(), request.getHitboxOffset()),
        request.getHitboxWidth(), request.getHitboxHeight()
    );
    tracker.track(new TrackedHologram(
        handle, textHandle, request.getInteractHandler(), text.getLocation(), request.getHitboxOffset(),
        text.getLifecycle()
    ));
    return handle;
  }

  @Override
  public void update(
      final InteractableHologramHandle handle,
      final FakeTextDisplayUpdate update
  ) {
    tracked(handle).ifPresent(hologram -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.updateText(viewer, hologram.getTextDisplayHandle(), update);
      }
    });
  }

  @Override
  public void updateHitbox(
      final InteractableHologramHandle handle,
      final float width,
      final float height
  ) {
    if (width <= 0.0F || height <= 0.0F) {
      throw new IllegalArgumentException("Hitbox dimensions must be positive");
    }
    tracked(handle).ifPresent(hologram -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.updateInteraction(viewer, handle.getInteractionEntityId(), width, height);
      }
    });
  }

  @Override
  public void updateHitboxOffset(
      final InteractableHologramHandle handle,
      final Vector offset
  ) {
    tracked(handle).ifPresent(hologram -> {
      hologram.setHitboxOffset(offset.clone());
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.teleport(
            viewer,
            handle.getInteractionEntityId(),
            interactionLocation(hologram.getLocation(), offset)
        );
      }
    });
  }

  @Override
  public void teleport(final InteractableHologramHandle handle, final Location location) {
    tracked(handle).ifPresent(hologram -> {
      hologram.setLocation(location.clone());
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.teleport(viewer, hologram.getTextDisplayHandle(), location);
        adapter.teleport(
            viewer,
            handle.getInteractionEntityId(),
            interactionLocation(location, hologram.getHitboxOffset())
        );
      }
    });
  }

  @Override
  public void remove(final InteractableHologramHandle handle) {
    requireOwner(handle);
    tracker.remove(handle).ifPresent(hologram -> {
      Player viewer = Bukkit.getPlayer(handle.getViewerId());
      if (viewer != null) {
        adapter.remove(
            viewer,
            handle.getTextDisplayEntityId(),
            handle.getInteractionEntityId()
        );
      }
    });
  }

  @Override
  public void removeAll(final Player viewer) {
    tracker.findOwned(owner, viewer.getUniqueId()).stream()
        .map(TrackedHologram::getHandle)
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    tracker.removeOwned(owner).forEach(hologram -> {
      Player viewer = Bukkit.getPlayer(hologram.getHandle().getViewerId());
      if (viewer != null) {
        adapter.remove(
            viewer,
            hologram.getHandle().getTextDisplayEntityId(),
            hologram.getHandle().getInteractionEntityId()
        );
      }
    });
  }

  private Optional<TrackedHologram> tracked(
      final InteractableHologramHandle handle
  ) {
    requireOwner(handle);
    return tracker.find(handle.getViewerId(), handle.getInteractionEntityId());
  }

  private void requireOwner(final InteractableHologramHandle handle) {
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Hologram handle belongs to another plugin");
    }
  }

  private static Location interactionLocation(final Location location, final Vector offset) {
    return location.clone().add(offset);
  }
}
