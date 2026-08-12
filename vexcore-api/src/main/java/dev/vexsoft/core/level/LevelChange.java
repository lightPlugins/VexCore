package dev.vexsoft.core.level;

import java.util.List;

/** Difference between two experience-derived level snapshots. */
public record LevelChange(
    LevelSnapshot previous,
    LevelSnapshot current,
    List<Integer> gainedLevels,
    List<Integer> lostLevels
) {

  /** Copies both level sequences. */
  public LevelChange {
    gainedLevels = List.copyOf(gainedLevels);
    lostLevels = List.copyOf(lostLevels);
  }

  /** Returns whether the derived level changed. */
  public boolean hasChanged() {
    return previous.level() != current.level();
  }
}
