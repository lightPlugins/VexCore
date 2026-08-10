package dev.vexsoft.core.paper.service.directory;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates pending player-directory requests sent to Velocity. */
public interface PlayerDirectoryCoordinatorService extends VexService {

  /** Requests one player's current backend. */
  CompletableFuture<Optional<NetworkPlayer>> find(UUID uniqueId);

  /** Returns the last snapshot immediately and refreshes it asynchronously when necessary. */
  List<NetworkPlayer> getOnlinePlayers();

  /** Completes one pending directory request. */
  void complete(PlayerDirectoryResponse response);

  /** Completes one pending network-player snapshot request. */
  void complete(PlayerDirectoryListResponse response);
}
