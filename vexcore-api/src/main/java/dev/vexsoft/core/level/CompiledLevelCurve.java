package dev.vexsoft.core.level;

/** Precomputed immutable level thresholds for fast runtime lookups. */
public interface CompiledLevelCurve {

  /** Returns the initial level requiring no claim. */
  int getMinimumLevel();

  /** Returns the highest supported level. */
  int getMaximumLevel();

  /** Calculates a complete snapshot from total experience. */
  LevelSnapshot calculate(double experience);

  /** Compares two total experience values. */
  LevelChange compare(double previousExperience, double currentExperience);

  /** Returns the total experience threshold for one supported level. */
  double getRequiredExperience(int level);
}
