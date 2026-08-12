package dev.vexsoft.core.requirement;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.execution.ExecutionDescription;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Runtime representation of one compiled requirement entry. */
public interface CompiledRequirement {

  /** Tests this requirement without mutating player state. */
  RequirementResult test(PlayerExecutionContext context);

  /** Renders this requirement and its current state. */
  Component describe(PlayerExecutionContext context);

  /** Supplies localization-ready entries including their current satisfied state. */
  default List<ExecutionDescription> describeEntries(final PlayerExecutionContext context) {
    Component fallback = describe(context);
    return List.of(new ExecutionDescription("", Map.of(), fallback));
  }
}
