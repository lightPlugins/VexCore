package dev.vexsoft.core.common.messaging.directory;

import java.util.Objects;
import java.util.UUID;

/** Requests the current backend server for one player. */
public record PlayerDirectoryRequest(UUID requestId, UUID playerId) {

  public PlayerDirectoryRequest {
    requestId = Objects.requireNonNull(requestId, "requestId");
    playerId = Objects.requireNonNull(playerId, "playerId");
  }
}
