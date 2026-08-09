package dev.vexsoft.core.paper.service.packets;


import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Dependencies(DisplayPacketAdapterService.class)
public final class VexTextDisplayPacketService implements TextDisplayPacketService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final Map<FakeDisplayHandle, Boolean> handles = new ConcurrentHashMap<>();

  public VexTextDisplayPacketService(final VexServiceRegistry services) {
    this.owner = services.getOwner();
    this.adapter = services.require(DisplayPacketAdapterService.class);
  }

  @Override
  public FakeDisplayHandle spawn(final Player viewer, final FakeTextDisplayRequest request) {
    FakeDisplayHandle handle = new FakeDisplayHandle(
        owner, viewer.getUniqueId(), adapter.allocateEntityId(), UUID.randomUUID(),
        FakeDisplayKind.TEXT
    );
    adapter.spawnText(viewer, handle, request);
    handles.put(handle, Boolean.TRUE);
    return handle;
  }

  @Override
  public void update(final FakeDisplayHandle handle, final FakeTextDisplayUpdate update) {
    Player viewer = viewer(handle);
    if (viewer != null && handles.containsKey(handle)) {
      adapter.updateText(viewer, handle, update);
    }
  }

  @Override
  public void teleport(final FakeDisplayHandle handle, final Location location) {
    Player viewer = viewer(handle);
    if (viewer != null && handles.containsKey(handle)) {
      adapter.teleport(viewer, handle, location);
    }
  }

  @Override
  public void remove(final FakeDisplayHandle handle) {
    if (handles.remove(handle) == null) {
      return;
    }
    Player viewer = viewer(handle);
    if (viewer != null) {
      adapter.remove(viewer, handle.getEntityId());
    }
  }

  @Override
  public void removeAll(final Player viewer) {
    handles.keySet().stream()
        .filter(handle -> handle.getViewerId().equals(viewer.getUniqueId()))
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    handles.keySet().stream().toList().forEach(this::remove);
    adapter.removeOwned(owner);
  }

  private Player viewer(final FakeDisplayHandle handle) {
    if (!handle.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Display handle belongs to another plugin");
    }
    return Bukkit.getPlayer(handle.getViewerId());
  }
}
