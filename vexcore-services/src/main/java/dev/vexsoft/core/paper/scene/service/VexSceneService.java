package dev.vexsoft.core.paper.scene.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.display.TextDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.player.CameraPacketService;
import dev.vexsoft.core.paper.scene.SceneDefinition;
import dev.vexsoft.core.paper.scene.ScenePlayback;
import dev.vexsoft.core.paper.service.actionbar.ActionBarService;
import dev.vexsoft.core.paper.service.scene.SceneService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Binds scene playback to the requesting plugin's lifetime. */
@Dependencies({SceneCoordinatorService.class, ScreenUiService.class, TextDisplayPacketService.class,
    CameraPacketService.class, ActionBarService.class})
public final class VexSceneService implements SceneService, AutoCloseable {

    private final SceneCoordinatorService coordinator;
    private final ScreenUiService screens;
    private final TextDisplayPacketService displays;
    private final CameraPacketService cameras;
    private final ActionBarService actionBars;
    private final ServiceOwner owner;
    private boolean closed;

    public VexSceneService(final VexServiceRegistry services) {
        coordinator = services.require(SceneCoordinatorService.class);
        screens = services.require(ScreenUiService.class);
        displays = services.require(TextDisplayPacketService.class);
        cameras = services.require(CameraPacketService.class);
        actionBars = services.require(ActionBarService.class);
        owner = services.getOwner();
    }

    @Override
    public synchronized Optional<ScenePlayback> play(final Player viewer, final SceneDefinition definition) {
        if (closed) {
            return Optional.empty();
        }
        var cinematic = definition.cinematic();
        var camera = cinematic == null ? null
            : new SceneCameraPlayback(viewer, cinematic, definition.durationMillis(), displays, cameras, actionBars);
        return coordinator.play(owner, screens, viewer, definition, camera);
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        coordinator.closeOwner(owner);
    }
}
