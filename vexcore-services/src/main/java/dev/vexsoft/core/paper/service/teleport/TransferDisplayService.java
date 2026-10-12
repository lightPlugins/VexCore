package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.teleport.TransferPresentation;
import java.util.UUID;

/** Owns transfer loading displays on backends without the initiating game-entry plugin. */
public interface TransferDisplayService extends VexService {

    /** Shows a loading bossbar only for the current native player binding. */
    void show(VexPlayer player, UUID request, TransferPresentation presentation);

    /** Removes or briefly fails only the matching transfer display. */
    void finish(VexPlayer player, UUID request, boolean successful);

    /** Retires loading UI owned by a disconnected native session. */
    default void disconnected(final VexPlayer player) {
    }
}
