package dev.vexsoft.core.common.data.global;

import java.util.Objects;

/** Holds a serialized global value and its optimistic-lock revision. */
public record StoredGlobalData(String value, long revision) {

  public StoredGlobalData {
    value = Objects.requireNonNull(value, "value");
    if (revision <= 0) {
      throw new IllegalArgumentException("Global data revision must be positive");
    }
  }
}
