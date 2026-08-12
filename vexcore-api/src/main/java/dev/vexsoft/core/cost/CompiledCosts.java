package dev.vexsoft.core.cost;

import java.util.List;

/** Immutable collection of compiled keyed costs. */
public record CompiledCosts(List<Entry> entries) {

  /** Copies all entries. */
  public CompiledCosts {
    entries = List.copyOf(entries);
  }

  /** Associates one configuration key with its compiled cost. */
  public record Entry(String key, CompiledCost cost) {}
}
