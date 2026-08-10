package dev.vexsoft.core.api.world;

import dev.vexsoft.core.api.network.ServerId;
import java.util.Objects;

/** Stores an exact position on one backend server and namespaced world. */
public record ServerPosition(
    ServerId server,
    WorldKey world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {

  /** Creates a validated server position. */
  public ServerPosition {
    server = Objects.requireNonNull(server, "server");
    world = Objects.requireNonNull(world, "world");
    requireFinite(x, "x");
    requireFinite(y, "y");
    requireFinite(z, "z");
    requireFinite(yaw, "yaw");
    requireFinite(pitch, "pitch");
  }

  private static void requireFinite(final double value, final String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Position " + name + " must be finite");
    }
  }
}
