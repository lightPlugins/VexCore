package dev.vexsoft.core.reactor;

import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Immutable configuration of one condition or effect component. */
@Value
@Builder
public class ReactionComponentDefinition {
  String id;
  @Singular
  Map<String, Object> arguments;
}
