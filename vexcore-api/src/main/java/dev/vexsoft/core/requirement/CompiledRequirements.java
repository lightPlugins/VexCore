package dev.vexsoft.core.requirement;

import java.util.List;

/** Immutable collection of compiled keyed requirements. */
public record CompiledRequirements(List<Entry> entries) {

  /** Copies all entries. */
  public CompiledRequirements {
    entries = List.copyOf(entries);
  }

  /** Associates one configuration key with its compiled requirement. */
  public record Entry(String key, CompiledRequirement requirement) {}
}
