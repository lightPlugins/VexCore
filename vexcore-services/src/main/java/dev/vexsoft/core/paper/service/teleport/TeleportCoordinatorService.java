package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates local Paper teleports and cross-server transfer callbacks. */
public interface TeleportCoordinatorService extends VexService {

    /** Starts a local or cross-server teleport. */
    CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination);

    /** Starts an admitted transfer with its explicitly supplied profile policy and loading presentation. */
    CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination, TransferOptions options);

    /** Rejects legacy unclaimed arrivals; current arrivals are consumed during profile initialization. */
    CompletableFuture<TeleportResult> acceptArrival(UUID playerId, ServerPosition destination);

    /** Completes a pending source-server transfer. */
    void complete(TeleportCompletion completion);

    /** Validates the replying backend before completing a pending handover. */
    void complete(TeleportCompletion completion, String sourceServer);

    /** Drains source transactions and persists a single-use handover before the proxy may connect. */
    CompletableFuture<Boolean> prepare(TeleportPreparation request);

    /** Cancels an unclaimed handover and asynchronously restores its still-connected source. */
    CompletableFuture<Void> abort(TeleportAbort request);
}
