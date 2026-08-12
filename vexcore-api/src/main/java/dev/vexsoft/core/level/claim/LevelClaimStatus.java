package dev.vexsoft.core.level.claim;

/** Outcome category for one level claim attempt. */
public enum LevelClaimStatus {
  READY,
  CLAIMED,
  NOT_AVAILABLE,
  REQUIREMENTS_NOT_MET,
  COSTS_NOT_AFFORDABLE,
  COST_FAILED,
  REWARD_FAILED
}
