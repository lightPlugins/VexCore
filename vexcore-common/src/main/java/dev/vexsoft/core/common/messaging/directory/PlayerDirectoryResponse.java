package dev.vexsoft.core.common.messaging.directory;

import java.util.Objects;
import java.util.UUID;

/** Reports a player's current backend ID and name, or empty values when they are offline. */
public record PlayerDirectoryResponse(
    UUID requestId,
    UUID playerId,
    String playerName,
    String serverId
) {

  public PlayerDirectoryResponse {
    requestId = Objects.requireNonNull(requestId, "requestId");
    playerId = Objects.requireNonNull(playerId, "playerId");
    playerName = Objects.requireNonNull(playerName, "playerName");
    serverId = Objects.requireNonNull(serverId, "serverId");
  }
}
