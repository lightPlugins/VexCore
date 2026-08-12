package dev.vexsoft.core.reward;

/** Opaque, mergeable contribution calculated by a contribution reward. */
public interface RewardContribution {

  /** Returns the registered reward key that owns this contribution. */
  String getKey();

  /** Combines two contributions of the same type without mutating either value. */
  RewardContribution merge(RewardContribution other);
}
