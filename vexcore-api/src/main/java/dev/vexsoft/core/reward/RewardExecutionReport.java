package dev.vexsoft.core.reward;

import java.util.Map;

/** Structured results produced while executing action rewards. */
public record RewardExecutionReport(Map<String, RewardResult> results) {

  /** Copies all keyed results. */
  public RewardExecutionReport {
    results = Map.copyOf(results);
  }

  /** Returns whether every attempted reward succeeded. */
  public boolean isSuccessful() {
    return results.values().stream().allMatch(result -> result.status() == RewardResult.Status.SUCCESS);
  }
}
