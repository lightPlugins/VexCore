package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.level.CompiledLevelCurve;
import dev.vexsoft.core.level.LevelChange;
import dev.vexsoft.core.level.LevelSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Immutable, precomputed implementation of a level curve. */
final class VexCompiledLevelCurve implements CompiledLevelCurve {

  private final int minimumLevel;
  private final int maximumLevel;
  private final double[] thresholds;

  VexCompiledLevelCurve(
      final int minimumLevel,
      final int maximumLevel,
      final double[] thresholds
  ) {
    this.minimumLevel = minimumLevel;
    this.maximumLevel = maximumLevel;
    this.thresholds = thresholds.clone();
  }

  @Override
  public int getMinimumLevel() {
    return minimumLevel;
  }

  @Override
  public int getMaximumLevel() {
    return maximumLevel;
  }

  @Override
  public LevelSnapshot calculate(final double experience) {
    if (!Double.isFinite(experience) || experience < 0.0D) {
      throw new IllegalArgumentException("experience must be a finite non-negative number");
    }
    int low = 0;
    int high = thresholds.length - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      if (thresholds[middle] <= experience) {
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    int index = Math.max(0, high);
    int level = minimumLevel + index;
    double start = thresholds[index];
    boolean maximum = level == maximumLevel;
    if (maximum) {
      return new LevelSnapshot(
          level, experience, start, start, experience - start, 0.0D, 1.0D, true
      );
    }
    double next = thresholds[index + 1];
    double required = next - start;
    double inLevel = experience - start;
    return new LevelSnapshot(
        level, experience, start, next, inLevel, required, inLevel / required, false
    );
  }

  @Override
  public LevelChange compare(final double previousExperience, final double currentExperience) {
    LevelSnapshot previous = calculate(previousExperience);
    LevelSnapshot current = calculate(currentExperience);
    List<Integer> gained = new ArrayList<>();
    List<Integer> lost = new ArrayList<>();
    for (int level = previous.level() + 1; level <= current.level(); level++) {
      gained.add(level);
    }
    for (int level = previous.level(); level > current.level(); level--) {
      lost.add(level);
    }
    return new LevelChange(previous, current, gained, lost);
  }

  @Override
  public double getRequiredExperience(final int level) {
    if (level < minimumLevel || level > maximumLevel) {
      throw new IllegalArgumentException("Unsupported level: " + level);
    }
    return thresholds[level - minimumLevel];
  }
}
