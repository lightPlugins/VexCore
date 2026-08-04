package dev.vexsoft.core.cache.internal;

/**
 * Provides lifecycle operations shared by managed cache implementations
 */
public interface ManagedVexCache {

  /** Invalidates every entry held by the managed cache */
  public void invalidateAll();

  /** Performs pending cache maintenance */
  public void cleanUp();
}
