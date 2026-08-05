package dev.vexsoft.core.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides a bounded cache that shares asynchronous loads between callers
 */
public interface VexAsyncCache<K, V> {

  /** Returns the owner-local name of this cache */
  public String getName();

  /** Returns an existing asynchronous value without triggering a load */
  public Optional<CompletableFuture<V>> getIfPresent(K key);

  /** Returns the cached value or starts one shared asynchronous load */
  public CompletableFuture<V> get(K key);

  /** Adds or replaces one completed value */
  public void put(K key, V value);

  /** Adds or replaces one asynchronous value */
  public void putFuture(K key, CompletableFuture<V> value);

  /** Returns an immutable snapshot of currently cached futures */
  public Map<K, CompletableFuture<V>> getAllPresent(Iterable<? extends K> keys);

  /** Removes one cached value */
  public void invalidate(K key);

  /** Removes multiple cached values */
  public void invalidateAll(Iterable<? extends K> keys);

  /** Removes every value from this cache */
  public void invalidateAll();

  /** Returns the estimated number of cached entries */
  public long getEstimatedSize();

  /** Returns a snapshot of the recorded cache statistics */
  public VexCacheStats getStats();

  /** Performs pending maintenance such as expiration and eviction */
  public void cleanUp();
}
