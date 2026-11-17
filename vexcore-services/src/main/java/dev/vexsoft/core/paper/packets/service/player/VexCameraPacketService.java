package dev.vexsoft.core.paper.packets.service.player;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.service.display.DisplayPacketAdapterService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    public void attach(final Player viewer, final FakeDisplayHandle target, final boolean hideSurvivalHud) {
        validate(viewer, target);
        Boolean previous = attachedViewers.put(viewer.getUniqueId(), hideSurvivalHud);

        if (Boolean.TRUE.equals(previous) && !hideSurvivalHud) {
            adapter.resetCamera(viewer, previous);
        }

        adapter.attachCamera(viewer, target, hideSurvivalHud);
    }

    @Override
    public void cut(final Player viewer, final FakeDisplayHandle target, final Location destination) {
        validate(viewer, target);
        adapter.cutCamera(viewer, target, destination);
    }

    @Override
    public void glide(final Player viewer, final FakeDisplayHandle target, final Location destination, final int durationTicks) {
        validate(viewer, target);
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Camera movement must last at least one tick");
        }
        adapter.glideCamera(viewer, target, destination, durationTicks);
    }

    @Override
    public void playSound(final Player viewer, final FakeDisplayHandle target, final Sound sound) {
        validate(viewer, target);
        adapter.playCameraSound(viewer, target, sound);
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

    private void validate(final Player viewer, final FakeDisplayHandle target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (!target.getOwner().equals(owner)) {
            throw new IllegalArgumentException("Camera target belongs to another plugin");
        }
        if (!target.getViewerId().equals(viewer.getUniqueId())) {
            throw new IllegalArgumentException("Camera target belongs to another viewer");
        }
    }
}
