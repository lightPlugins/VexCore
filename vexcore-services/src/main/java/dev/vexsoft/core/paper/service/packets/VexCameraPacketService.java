package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.service.CameraPacketService;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Default owner-scoped client camera service. */
@Dependencies(DisplayPacketAdapterService.class)
public final class VexCameraPacketService implements CameraPacketService, AutoCloseable {

  private final ServiceOwner owner;
  private final DisplayPacketAdapterService adapter;
  private final Map<UUID, Boolean> attachedViewers = new ConcurrentHashMap<>();

  /** Creates the camera service through VexCore's service registry. */
  public VexCameraPacketService(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    owner = checked.getOwner();
    adapter = checked.require(DisplayPacketAdapterService.class);
  }

  @Override
  public void attach(
      final Player viewer,
      final FakeDisplayHandle target,
      final boolean hideSurvivalHud
  ) {
    Player checkedViewer = Objects.requireNonNull(viewer, "viewer");
    FakeDisplayHandle checkedTarget = Objects.requireNonNull(target, "target");
    if (!checkedTarget.getOwner().equals(owner)) {
      throw new IllegalArgumentException("Camera target belongs to another plugin");
    }
    if (!checkedTarget.getViewerId().equals(checkedViewer.getUniqueId())) {
      throw new IllegalArgumentException("Camera target belongs to another viewer");
    }
    Boolean previous = attachedViewers.put(checkedViewer.getUniqueId(), hideSurvivalHud);
    if (Boolean.TRUE.equals(previous) && !hideSurvivalHud) {
      adapter.resetCamera(checkedViewer, previous);
    }
    adapter.attachCamera(checkedViewer, checkedTarget, hideSurvivalHud);
  }

  @Override
  public void reset(final Player viewer) {
    Player checkedViewer = Objects.requireNonNull(viewer, "viewer");
    Boolean hiddenHud = attachedViewers.get(checkedViewer.getUniqueId());
    if (hiddenHud != null && checkedViewer.isOnline()) {
      adapter.resetCamera(checkedViewer, hiddenHud);
    }
    if (hiddenHud != null) {
      attachedViewers.remove(checkedViewer.getUniqueId(), hiddenHud);
    }
  }

  @Override
  public void close() {
    attachedViewers.keySet().stream().toList().forEach(viewerId -> {
      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer != null) {
        reset(viewer);
      } else {
        attachedViewers.remove(viewerId);
      }
    });
  }
}
