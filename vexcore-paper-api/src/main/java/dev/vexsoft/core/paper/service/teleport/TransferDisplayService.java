package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.teleport.TransferPresentation;
import java.util.UUID;

/** Owns request-scoped loading displays for backend actions and server transfers. */
public interface TransferDisplayService extends VexService {

    /** Shows a loading bossbar only for the current native player binding. */
    void show(VexPlayer player, UUID request, TransferPresentation presentation);

    /** Removes matching progress and plays optional arrival audio on success, or briefly displays failure. */
    void finish(VexPlayer player, UUID request, boolean successful);

    /** Retires loading UI owned by a disconnected native session. */
    default void disconnected(final VexPlayer player) {
    }
}
