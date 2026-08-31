package dev.vexsoft.core.paper.mob;

import java.util.Objects;
import java.util.UUID;

/** Stable handle for one non-persistent runtime mob. */
public record MobHandle(UUID instanceId, MobKey definitionKey) {

  /** Creates and validates a runtime handle. */
  public MobHandle {
    Objects.requireNonNull(instanceId, "instanceId");
    Objects.requireNonNull(definitionKey, "definitionKey");
  }
}
