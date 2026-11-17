package dev.vexsoft.core.paper.packets.service.player;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
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

    /** Moves the camera target instantly while preserving its interpolated movement for later updates. */
    void cut(Player viewer, FakeDisplayHandle target, Location destination);

    /** Moves the camera using one linear interpolation on the client for the given positive tick count. */
    void glide(Player viewer, FakeDisplayHandle target, Location destination, int durationTicks);

    /** Plays a sound from the camera target so it follows the listener through movement and cuts. */
    void playSound(Player viewer, FakeDisplayHandle target, Sound sound);

    /** Restores the viewer's real camera and current server-side game-mode presentation. */
    void reset(Player viewer);
}
