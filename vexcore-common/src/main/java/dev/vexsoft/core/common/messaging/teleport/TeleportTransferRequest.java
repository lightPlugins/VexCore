package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Objects;
import java.util.UUID;

/** Requests that Velocity transfers a player and forwards an arrival destination. */
public record TeleportTransferRequest(
    UUID requestId,
    UUID playerId,
    ServerPosition destination,
    String sourceServer
) {

  public TeleportTransferRequest {
    requestId = Objects.requireNonNull(requestId, "requestId");
    playerId = Objects.requireNonNull(playerId, "playerId");
    destination = Objects.requireNonNull(destination, "destination");
    sourceServer = Objects.requireNonNull(sourceServer, "sourceServer");
  }
}
