package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import org.bukkit.entity.Player;

/** Owner-scoped control of a player's client camera using viewer-local display entities. */
public interface CameraPacketService extends VexService {

    /**
     * Attaches the viewer's camera to one of this plugin's fake displays.
     *
     * @param viewer          camera viewer
     * @param target          viewer-local camera target
     * @param hideSurvivalHud whether to spoof spectator presentation on the client
     */
    void attach(Player viewer, FakeDisplayHandle target, boolean hideSurvivalHud);

    /** Restores the viewer's real camera and current server-side game-mode presentation. */
    void reset(Player viewer);
}
