package dev.vexsoft.core.level;

import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.reward.CompiledRewards;

/** Runtime representation of one repeating level claim rule. */
public record CompiledLevelRule(
    int minimumLevel,
    int step,
    CompiledRequirements requirements,
    CompiledCosts costs,
    CompiledRewards rewards
) {

  /** Returns whether this rule applies to the supplied level. */
  public boolean matches(final int level) {
    return level >= minimumLevel && (level - minimumLevel) % step == 0;
  }
}
