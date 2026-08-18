package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.service.BlockDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Default owner-scoped service for viewer-specific virtual block displays. */
@Dependencies(DisplayPacketAdapterService.class)
public final class VexBlockDisplayPacketService
    implements BlockDisplayPacketService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final Set<FakeDisplayHandle> handles = ConcurrentHashMap.newKeySet();

  /** Creates the block-display service through VexCore's service registry. */
  public VexBlockDisplayPacketService(final VexServiceRegistry services) {
    owner = services.getOwner();
    adapter = services.require(DisplayPacketAdapterService.class);
  }

  @Override
  public FakeDisplayHandle spawn(
      final Player viewer,
      final FakeBlockDisplayRequest request
  ) {
    FakeDisplayHandle handle = new FakeDisplayHandle(
        owner,
        viewer.getUniqueId(),
        adapter.allocateEntityId(),
        UUID.randomUUID(),
        FakeDisplayKind.BLOCK
    );
    adapter.spawnBlock(viewer, handle, request);
    handles.add(handle);
    return handle;
  }

  @Override
  public void update(final FakeDisplayHandle handle, final FakeBlockDisplayUpdate update) {
    Player viewer = viewer(handle);
    if (viewer != null && handles.contains(handle)) {
      adapter.updateBlock(viewer, handle, update);
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
    if (handle.getKind() != FakeDisplayKind.BLOCK) {
      throw new IllegalArgumentException("Display handle is not a block display");
    }
    return Bukkit.getPlayer(handle.getViewerId());
  }
}
