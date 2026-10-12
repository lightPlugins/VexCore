package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.concurrent.CompletableFuture;

/** Teleports loaded Vex players locally or through Velocity to another backend server. */
public interface PlayerTeleportService extends VexService {

    /**
     * Teleports without blocking server threads and completes after destination restoration and asynchronous teleport.
     */
    CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination);

    /** Starts an admitted transfer with its explicitly supplied profile policy and loading presentation. */
    CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination, TransferOptions options);
}
