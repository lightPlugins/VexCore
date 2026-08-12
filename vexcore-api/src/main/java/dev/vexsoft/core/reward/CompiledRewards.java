package dev.vexsoft.core.reward;

import java.util.List;

/** Immutable collection of compiled keyed rewards. */
public record CompiledRewards(List<Entry> entries) {

  /** Copies all entries. */
  public CompiledRewards {
    entries = List.copyOf(entries);
  }

  /** Associates one configuration key with its compiled reward. */
  public record Entry(String key, CompiledReward reward) {}
}
