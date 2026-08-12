package dev.vexsoft.core.level;

/** Controls claimed levels after a rebalanced experience curve lowers the available level. */
public enum ClaimedLevelOverflowPolicy {
  /** Retains already claimed progression after a curve change. */
  KEEP,
  /** Limits claimed progression to the newly available level. */
  CLAMP
}
