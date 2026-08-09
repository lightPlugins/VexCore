package dev.vexsoft.core.reactor;

import java.util.List;
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
  List<ReactionTriggerDefinition> triggers;
  @Singular
  List<ReactionComponentDefinition> conditions;
  @Singular
  List<ReactionComponentDefinition> effects;
}
