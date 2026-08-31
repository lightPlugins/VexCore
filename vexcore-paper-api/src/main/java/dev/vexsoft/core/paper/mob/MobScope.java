package dev.vexsoft.core.paper.mob;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Controls which players may see and interact with one mob instance. */
public final class MobScope {

  private static final MobScope GLOBAL = new MobScope(null);
  private final UUID playerId;

  private MobScope(final UUID playerId) {
    this.playerId = playerId;
  }

  /** Creates the shared scope visible to every eligible viewer. */
  public static MobScope global() {
    return GLOBAL;
  }

  /** Creates a scope visible only to the supplied player. */
  public static MobScope player(final UUID playerId) {
    return new MobScope(Objects.requireNonNull(playerId, "playerId"));
  }

  /** Returns whether this scope is global. */
  public boolean isGlobal() {
    return playerId == null;
  }

  /** Returns the personal owner when this is not a global scope. */
  public Optional<UUID> playerId() {
    return Optional.ofNullable(playerId);
  }

  /** Returns whether the supplied player belongs to this scope. */
  public boolean includes(final UUID candidate) {
    return isGlobal() || Objects.equals(playerId, candidate);
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof MobScope scope && Objects.equals(playerId, scope.playerId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(playerId);
  }

  @Override
  public String toString() {
    return isGlobal() ? "global" : "player:" + playerId;
  }
}
