package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Owns a plugin's interactive canvas sessions and releases them when that plugin scope closes. */
public interface InteractiveUiService extends VexService {

    /**
     * Opens a centered canvas using the separate InteractiveUI pack, on the player's entity scheduler.
     * Requires a Survival player on foot with an empty main hand; another owner's active session is rejected.
     */
    InteractiveUi open(Player player, String id);

    /** Finds this owner's active canvas with the given identifier. */
    Optional<InteractiveUi> find(Player player, String id);
}
