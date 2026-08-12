package dev.vexsoft.core.cost;

import java.util.List;

/** Aggregate check or consumption outcome for a configured cost section. */
public record CostExecutionResult(boolean successful, List<Entry> entries) {

  /** Copies all keyed outcomes. */
  public CostExecutionResult {
    entries = List.copyOf(entries);
  }

  /** Associates one cost key with its outcome. */
  public record Entry(String key, boolean successful, String message) {}
}
