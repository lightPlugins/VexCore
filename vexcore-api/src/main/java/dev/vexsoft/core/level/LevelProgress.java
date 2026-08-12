package dev.vexsoft.core.level;

/** Immutable view of progression data owned and persisted by an external plugin. */
public interface LevelProgress {

  /** Returns total experience used to derive the available level. */
  double getExperience();

  /** Returns the highest level whose claim was completed. */
  int getClaimedLevel();
}
