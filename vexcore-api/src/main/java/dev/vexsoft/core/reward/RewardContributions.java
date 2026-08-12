package dev.vexsoft.core.reward;

import java.util.Map;
import java.util.Optional;

/** Aggregated reconstructable contributions keyed by reward type. */
public record RewardContributions(Map<String, RewardContribution> values) {

  /** Copies all contributions. */
  public RewardContributions {
    values = Map.copyOf(values);
  }

  /** Finds and casts one contribution to its public domain type. */
  public <T extends RewardContribution> Optional<T> find(
      final String key,
      final Class<T> type
  ) {
    RewardContribution contribution = values.get(key);
    return type.isInstance(contribution) ? Optional.of(type.cast(contribution)) : Optional.empty();
  }
}
