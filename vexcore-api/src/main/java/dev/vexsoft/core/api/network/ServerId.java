package dev.vexsoft.core.api.network;

import java.util.Locale;
import java.util.Objects;

/** Identifies one backend server in the VexCore network. */
public record ServerId(String value) {

  /** Creates and validates a server ID. */
  public ServerId {
    value = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
      throw new IllegalArgumentException("Invalid server ID: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
