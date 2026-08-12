package dev.vexsoft.core.level;

import java.util.Map;

/** Defines repeating requirements, costs, and rewards for claimable levels. */
public record LevelRuleDefinition(
    int minimumLevel,
    int step,
    Map<String, Object> requirements,
    Map<String, Object> costs,
    Map<String, Object> rewards
) {

  /** Validates the rule range. */
  public LevelRuleDefinition {
    if (minimumLevel < 0) {
      throw new IllegalArgumentException("minimumLevel must not be negative");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("step must be greater than zero");
    }
    requirements = requirements == null ? Map.of() : Map.copyOf(requirements);
    costs = costs == null ? Map.of() : Map.copyOf(costs);
    rewards = rewards == null ? Map.of() : Map.copyOf(rewards);
  }

  /** Returns whether this repeating rule applies to the supplied level. */
  public boolean matches(final int level) {
    return level >= minimumLevel && (level - minimumLevel) % step == 0;
  }
}
