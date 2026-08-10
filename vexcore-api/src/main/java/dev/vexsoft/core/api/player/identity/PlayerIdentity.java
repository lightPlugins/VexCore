package dev.vexsoft.core.api.player.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stores a player's stable UUID and most recently observed name. */
public record PlayerIdentity(UUID uniqueId, String name, Instant updatedAt) {

  /** Creates a validated player identity. */
  public PlayerIdentity {
    uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    name = Objects.requireNonNull(name, "name");
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }
}
