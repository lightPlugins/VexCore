package dev.vexsoft.core.level.claim;

import dev.vexsoft.core.reward.RewardExecutionReport;

/** Result of one sequential claim attempt. */
public record LevelClaimResult(
    int level,
    LevelClaimStatus status,
    LevelClaimPreview preview,
    RewardExecutionReport rewards
) {

  /** Returns whether the claimed-level progress was advanced. */
  public boolean isSuccessful() {
    return status == LevelClaimStatus.CLAIMED;
  }
}
