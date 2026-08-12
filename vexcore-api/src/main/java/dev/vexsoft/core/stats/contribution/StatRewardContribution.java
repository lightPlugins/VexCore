package dev.vexsoft.core.stats.contribution;

import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregated runtime stat modifiers calculated from {@code stats} rewards. */
public record StatRewardContribution(Map<StatKey, StatModifier> modifiers)
    implements RewardContribution {

  /** Copies all modifiers. */
  public StatRewardContribution {
    modifiers = Map.copyOf(modifiers);
  }

  @Override
  public String getKey() {
    return "stats";
  }

  @Override
  public RewardContribution merge(final RewardContribution other) {
    if (!(other instanceof StatRewardContribution stats)) {
      throw new IllegalArgumentException("Cannot merge stats with " + other.getKey());
    }
    Map<StatKey, StatModifier> merged = new LinkedHashMap<>(modifiers);
    stats.modifiers.forEach((key, modifier) -> merged.merge(key, modifier, (left, right) -> {
      if (left.operation() != right.operation()) {
        throw new IllegalArgumentException("Mixed stat operations for " + key);
      }
      return switch (left.operation()) {
        case FLAT, ADDITIVE_MULTIPLIER -> new StatModifier(
            left.operation(),
            left.amount() + right.amount()
        );
        case TOTAL_MULTIPLIER -> StatModifier.totalMultiplier(left.amount() * right.amount());
      };
    }));
    return new StatRewardContribution(merged);
  }
}
