package dev.vexsoft.core.level;

import java.util.List;
import java.util.Objects;

/** Configuration-format-independent definition of one level curve and its claim rules. */
public record LevelDefinition(
    int minimumLevel,
    int maximumLevel,
    String requiredExperience,
    LevelClaimMode claimMode,
    ClaimedLevelOverflowPolicy overflowPolicy,
    List<LevelRuleDefinition> rules
) {

  /** Validates and copies the definition. */
  public LevelDefinition {
    if (minimumLevel < 0) {
      throw new IllegalArgumentException("minimumLevel must not be negative");
    }
    if (maximumLevel <= minimumLevel) {
      throw new IllegalArgumentException("maximumLevel must be greater than minimumLevel");
    }
    requiredExperience = Objects.requireNonNull(requiredExperience, "requiredExperience");
    claimMode = Objects.requireNonNull(claimMode, "claimMode");
    overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy");
    rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }
}
