package dev.vexsoft.core.paper.service.directory;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates pending player-directory requests sent to Velocity. */
public interface PlayerDirectoryCoordinatorService extends VexService {

  /** Requests one player's current backend. */
  CompletableFuture<Optional<NetworkPlayer>> find(UUID uniqueId);

  /** Completes one pending directory request. */
  void complete(PlayerDirectoryResponse response);
}
