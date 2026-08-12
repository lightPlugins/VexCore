package dev.vexsoft.core.requirement;

import java.util.Map;

/** Aggregate outcome for a configured requirement section. */
public record RequirementExecutionResult(boolean satisfied, Map<String, RequirementResult> results) {

  /** Copies all keyed outcomes. */
  public RequirementExecutionResult {
    results = Map.copyOf(results);
  }
}
