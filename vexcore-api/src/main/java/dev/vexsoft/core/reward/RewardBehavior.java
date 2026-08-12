package dev.vexsoft.core.reward;

/** Defines how a reward participates in progression processing. */
public enum RewardBehavior {
  /** Irreversible action executed only for newly earned progression. */
  ACTION,
  /** Reconstructable state recalculated on join, reload, and relevant data changes. */
  CONTRIBUTION
}
