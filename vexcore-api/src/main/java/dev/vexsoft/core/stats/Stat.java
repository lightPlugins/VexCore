package dev.vexsoft.core.stats;

/** One active runtime registration of a stable stat key. */
public interface Stat {

  /** Returns the stable persistence key. */
  StatKey getKey();

  /** Returns the current immutable definition. */
  StatDefinition getDefinition();

  /** Returns the dense slot used by loaded player containers. */
  int getRuntimeId();

  /** Returns whether this exact runtime registration remains active. */
  boolean isRegistered();
}
