package dev.vexsoft.core.api.network;

import java.util.Objects;
import java.util.UUID;

/** Identifies an online player and the backend currently hosting them. */
public record NetworkPlayer(UUID uniqueId, String name, ServerId server) {

  /** Creates a validated network player location. */
  public NetworkPlayer {
    uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    name = Objects.requireNonNull(name, "name").trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Player name must not be empty");
    }
    server = Objects.requireNonNull(server, "server");
  }
}
