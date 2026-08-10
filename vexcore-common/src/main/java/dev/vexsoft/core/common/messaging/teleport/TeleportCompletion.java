package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.teleport.TeleportStatus;
import java.util.Objects;
import java.util.UUID;

/** Reports the final destination-server result to the initiating backend. */
public record TeleportCompletion(UUID requestId, TeleportStatus status, String message) {

  public TeleportCompletion {
    requestId = Objects.requireNonNull(requestId, "requestId");
    status = Objects.requireNonNull(status, "status");
    message = Objects.requireNonNull(message, "message");
  }
}
