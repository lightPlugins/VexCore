package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Provides the localized, opt-in interactive UI acceptance demo. */
public interface InteractiveUiDemoService extends VexService {

    /** Opens the demo on the player's owning thread and reports unsupported player states. */
    void start(Player player);

    /** Closes the player's demo and reports whether a demo was active. */
    void stop(Player player);

    /** Sends a localized snapshot of the player's current demo input state. */
    void status(Player player);
}
