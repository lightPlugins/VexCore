package dev.vexsoft.core.stats.contribution;

import dev.vexsoft.core.stats.StatKey;
import java.util.Set;

/** Result of atomically replacing one provider snapshot. */
public record StatContributionRefreshResult(
    String source,
    Set<StatKey> changedStats,
    boolean successful,
    String message
) {

  /** Copies the changed stat keys. */
  public StatContributionRefreshResult {
    changedStats = Set.copyOf(changedStats);
  }
}
