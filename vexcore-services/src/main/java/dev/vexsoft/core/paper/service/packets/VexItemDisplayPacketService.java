package dev.vexsoft.core.paper.service.packets;


import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Dependencies(DisplayPacketAdapterService.class)
public final class VexItemDisplayPacketService implements ItemDisplayPacketService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final Set<FakeDisplayHandle> handles = ConcurrentHashMap.newKeySet();

  public VexItemDisplayPacketService(final VexServiceRegistry services) {
    this.owner = services.getOwner();
    this.adapter = services.require(DisplayPacketAdapterService.class);
  }

  @Override
  public FakeDisplayHandle spawn(final Player viewer, final FakeItemDisplayRequest request) {
    FakeDisplayHandle handle = new FakeDisplayHandle(
        owner, viewer.getUniqueId(), adapter.allocateEntityId(), UUID.randomUUID(),
        FakeDisplayKind.ITEM
    );
    adapter.spawnItem(viewer, handle, request);
    handles.add(handle);
    return handle;
  }

  @Override
  public void update(final FakeDisplayHandle handle, final FakeItemDisplayUpdate update) {
    Player viewer = viewer(handle);
    if (viewer != null && handles.contains(handle)) {
      adapter.updateItem(viewer, handle, update);
    }
  }

  @Override
  public void teleport(final FakeDisplayHandle handle, final Location location) {
    Player viewer = viewer(handle);
    if (viewer != null && handles.contains(handle)) {
      adapter.teleport(viewer, handle, location);
    }
  }

  @Override
  public void remove(final FakeDisplayHandle handle) {
    if (!handles.remove(handle)) {
      return;
    }
    Player viewer = viewer(handle);
    if (viewer != null) {
      adapter.remove(viewer, handle.getEntityId());
    }
  }

  @Override
  public void removeAll(final Player viewer) {
    handles.stream()
        .filter(handle -> handle.getViewerId().equals(viewer.getUniqueId()))
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    handles.stream().toList().forEach(this::remove);
    adapter.removeOwned(owner);
  }

  private Player viewer(final FakeDisplayHandle handle) {
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Display handle belongs to another plugin");
    }
    return Bukkit.getPlayer(handle.getViewerId());
  }
}
