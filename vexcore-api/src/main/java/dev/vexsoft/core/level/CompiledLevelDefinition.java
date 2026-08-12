package dev.vexsoft.core.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable compiled level curve, policies, and repeating claim rules. */
public record CompiledLevelDefinition(
    CompiledLevelCurve curve,
    LevelClaimMode claimMode,
    ClaimedLevelOverflowPolicy overflowPolicy,
    List<CompiledLevelRule> rules
) {

  /** Validates and copies all parts. */
  public CompiledLevelDefinition {
    Objects.requireNonNull(curve, "curve");
    Objects.requireNonNull(claimMode, "claimMode");
    Objects.requireNonNull(overflowPolicy, "overflowPolicy");
    rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }

  /** Returns every rule applying to one level in declaration order. */
  public List<CompiledLevelRule> getRules(final int level) {
    List<CompiledLevelRule> matching = new ArrayList<>();
    for (CompiledLevelRule rule : rules) {
      if (rule.matches(level)) {
        matching.add(rule);
      }
    }
    return List.copyOf(matching);
  }
}
