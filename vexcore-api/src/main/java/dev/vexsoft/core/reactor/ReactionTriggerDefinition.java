package dev.vexsoft.core.reactor;

import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Immutable trigger and filter configuration for one reaction branch. */
@Value
@Builder
public class ReactionTriggerDefinition {
  String id;
  @Singular
  Map<String, Object> filters;
}
