package dev.vexsoft.core.common.messaging.directory;

import java.util.Objects;
import java.util.UUID;

/** Requests a snapshot of every player currently connected through Velocity. */
public record PlayerDirectoryListRequest(UUID requestId) {

  public PlayerDirectoryListRequest {
    requestId = Objects.requireNonNull(requestId, "requestId");
  }
}
