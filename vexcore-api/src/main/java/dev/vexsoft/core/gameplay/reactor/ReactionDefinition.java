package dev.vexsoft.core.gameplay.reactor;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Immutable, configuration-format-independent definition of one gameplay reaction. */
@Value
@Builder
public class ReactionDefinition {
  String id;
  @Builder.Default
  boolean enabled = true;
  @Singular
  List<String> triggers;
  @Singular
  Map<String, Object> filters;
  @Singular
  List<ReactionComponentDefinition> conditions;
  @Singular
  List<ReactionComponentDefinition> effects;
}
