package dev.vexsoft.core.common.messaging.directory;

import dev.vexsoft.core.api.network.NetworkPlayer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Contains an immutable snapshot of players currently connected through Velocity. */
public record PlayerDirectoryListResponse(UUID requestId, List<NetworkPlayer> players) {

  public PlayerDirectoryListResponse {
    requestId = Objects.requireNonNull(requestId, "requestId");
    players = List.copyOf(Objects.requireNonNull(players, "players"));
  }
}
