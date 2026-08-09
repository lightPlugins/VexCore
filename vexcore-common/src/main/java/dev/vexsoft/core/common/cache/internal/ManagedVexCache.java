package dev.vexsoft.core.common.cache.internal;

/**
 * Provides lifecycle operations shared by managed cache implementations
 */
public interface ManagedVexCache {

  /** Invalidates every entry held by the managed cache */
  void invalidateAll();

  /** Performs pending cache maintenance */
  void cleanUp();
}
