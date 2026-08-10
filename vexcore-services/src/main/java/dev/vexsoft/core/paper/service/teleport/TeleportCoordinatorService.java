package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates local Paper teleports and cross-server transfer callbacks. */
public interface TeleportCoordinatorService extends VexService {

  /** Starts a local or cross-server teleport. */
  CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination);

  /** Finishes an arrival teleport for a player that has reached this backend. */
  CompletableFuture<TeleportResult> acceptArrival(UUID playerId, ServerPosition destination);

  /** Completes a pending source-server transfer. */
  void complete(TeleportCompletion completion);
}
