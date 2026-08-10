package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Objects;
import java.util.UUID;

/** Instructs the destination backend to finish a transferred player's teleport. */
public record TeleportArrival(
    UUID requestId,
    UUID playerId,
    ServerPosition destination,
    String sourceServer
) {

  public TeleportArrival {
    requestId = Objects.requireNonNull(requestId, "requestId");
    playerId = Objects.requireNonNull(playerId, "playerId");
    destination = Objects.requireNonNull(destination, "destination");
    sourceServer = Objects.requireNonNull(sourceServer, "sourceServer");
  }
}
