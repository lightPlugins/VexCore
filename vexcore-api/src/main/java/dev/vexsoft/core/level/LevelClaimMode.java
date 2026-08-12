package dev.vexsoft.core.level;

/** Controls whether reached levels wait for player interaction. */
public enum LevelClaimMode {
  /** Reached levels remain pending until explicitly claimed. */
  MANUAL,
  /** Reached levels may be processed immediately by the owning progression system. */
  AUTOMATIC
}
