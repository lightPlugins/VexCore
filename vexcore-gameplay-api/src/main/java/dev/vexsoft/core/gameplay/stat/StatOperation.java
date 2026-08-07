package dev.vexsoft.core.gameplay.stat;

/** Supported stages of the stat calculation pipeline. */
public enum StatOperation {
  /** Adds a numeric value before multipliers are evaluated. */
  FLAT,
  /** Adds to the shared multiplier, where {@code 0.15} means fifteen percent. */
  ADDITIVE_MULTIPLIER,
  /** Multiplies the complete result, where {@code 1.10} means ten percent more. */
  TOTAL_MULTIPLIER
}
