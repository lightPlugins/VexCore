package dev.vexsoft.core.paper.service.scene;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.scene.SceneDefinition;
import dev.vexsoft.core.paper.scene.ScenePlayback;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Plays caller-defined scenes through the caller's scoped ScreenUI service. */
public interface SceneService extends VexService {

    /** Starts a scene on the viewer's entity thread if the viewer has no other active scene. */
    Optional<ScenePlayback> play(Player viewer, SceneDefinition definition);
}
