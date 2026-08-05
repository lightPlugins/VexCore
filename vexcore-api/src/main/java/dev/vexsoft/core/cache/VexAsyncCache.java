package dev.vexsoft.core.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides a bounded cache that shares asynchronous loads between callers
 */
public interface VexAsyncCache<K, V> {

  /** Returns the owner-local name of this cache */
  String getName();

  /** Returns an existing asynchronous value without triggering a load */
  Optional<CompletableFuture<V>> getIfPresent(K key);

  /** Returns the cached value or starts one shared asynchronous load */
  CompletableFuture<V> get(K key);

  /** Adds or replaces one completed value */
  void put(K key, V value);

  /** Adds or replaces one asynchronous value */
  void putFuture(K key, CompletableFuture<V> value);

  /** Returns an immutable snapshot of currently cached futures */
  Map<K, CompletableFuture<V>> getAllPresent(Iterable<? extends K> keys);

  /** Removes one cached value */
  void invalidate(K key);

  /** Removes multiple cached values */
  void invalidateAll(Iterable<? extends K> keys);

  /** Removes every value from this cache */
  void invalidateAll();

  /** Returns the estimated number of cached entries */
  long getEstimatedSize();

  /** Returns a snapshot of the recorded cache statistics */
  VexCacheStats getStats();

  /** Performs pending maintenance such as expiration and eviction */
  void cleanUp();
}
