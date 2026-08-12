package dev.vexsoft.core.requirement;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import net.kyori.adventure.text.Component;

/** Runtime representation of one compiled requirement entry. */
public interface CompiledRequirement {

  /** Tests this requirement without mutating player state. */
  RequirementResult test(PlayerExecutionContext context);

  /** Renders this requirement and its current state. */
  Component describe(PlayerExecutionContext context);
}
