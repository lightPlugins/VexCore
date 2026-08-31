package dev.vexsoft.core.paper.mob;

import java.util.Objects;
import org.bukkit.Location;

/** Immutable request to create one non-persistent custom mob. */
public record MobSpawnRequest(
    MobKey definitionKey,
    Location location,
    MobScope scope,
    Location movementOrigin
) {

  /** Creates a defensive spawn request. */
  public MobSpawnRequest {
    Objects.requireNonNull(definitionKey, "definitionKey");
    location = Objects.requireNonNull(location, "location").clone();
    if (location.getWorld() == null) {
      throw new IllegalArgumentException("location must have a world");
    }
    Objects.requireNonNull(scope, "scope");
    movementOrigin = Objects.requireNonNull(movementOrigin, "movementOrigin").clone();
    if (movementOrigin.getWorld() != location.getWorld()) {
      throw new IllegalArgumentException("movementOrigin must be in the spawn world");
    }
  }

  /** Creates a request whose movement origin is its spawn position. */
  public MobSpawnRequest(
      final MobKey definitionKey,
      final Location location,
      final MobScope scope
  ) {
    this(definitionKey, location, scope, location);
  }

  /** Creates a global spawn request. */
  public static MobSpawnRequest global(final MobKey key, final Location location) {
    return new MobSpawnRequest(key, location, MobScope.global(), location);
  }

  @Override
  public Location location() {
    return location.clone();
  }

  @Override
  public Location movementOrigin() {
    return movementOrigin.clone();
  }
}
