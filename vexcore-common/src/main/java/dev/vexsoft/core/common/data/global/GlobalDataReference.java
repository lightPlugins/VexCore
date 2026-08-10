package dev.vexsoft.core.common.data.global;

import java.util.Objects;

/** Identifies one persisted global value without exposing its payload. */
public record GlobalDataReference(String owner, String key) {

  public GlobalDataReference {
    owner = Objects.requireNonNull(owner, "owner");
    key = Objects.requireNonNull(key, "key");
  }
}
