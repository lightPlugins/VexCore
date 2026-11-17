package dev.vexsoft.core.paper.scene.service;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.scene.SceneDefinition;
import dev.vexsoft.core.paper.scene.ScenePlayback;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Owns the single active scene slot for each connected viewer. */
public interface SceneCoordinatorService extends VexService {

    /** Opens the scene using the requesting plugin's UI scope and optional cinematic presentation. */
    Optional<ScenePlayback> play(
        ServiceOwner owner, ScreenUiService screens, Player viewer, SceneDefinition definition, SceneCameraPlayback camera
    );

    /** Reveals the current caption and reports whether a scene handled the input. */
    boolean reveal(Player viewer);

    /** Interrupts the scene belonging to this connection. */
    void interrupt(Player viewer);

    /** Releases all scenes owned by a closing plugin. */
    void closeOwner(ServiceOwner owner);
}
