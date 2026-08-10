package dev.vexsoft.core.common.messaging.directory;

import java.util.Objects;
import java.util.UUID;

/** Reports a player's current backend ID, or an empty ID when they are offline. */
public record PlayerDirectoryResponse(UUID requestId, UUID playerId, String serverId) {

  public PlayerDirectoryResponse {
    requestId = Objects.requireNonNull(requestId, "requestId");
    playerId = Objects.requireNonNull(playerId, "playerId");
    serverId = Objects.requireNonNull(serverId, "serverId");
  }
}
