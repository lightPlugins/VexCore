package dev.vexsoft.core.level.claim;

import java.util.List;

/** Ordered results from claiming every currently reachable sequential level. */
public record LevelClaimBatchResult(List<LevelClaimResult> results) {

  /** Copies all attempts. */
  public LevelClaimBatchResult {
    results = List.copyOf(results);
  }

  /** Returns the number of successfully claimed levels. */
  public long getClaimedCount() {
    return results.stream().filter(LevelClaimResult::isSuccessful).count();
  }
}
