package dev.vexsoft.core.cache;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a bounded synchronous cache owned by one Vex plugin
 */
public interface VexCache<K, V> {

  /** Returns the owner-local name of this cache */
  String getName();

  /** Returns a cached value without triggering a load */
  Optional<V> getIfPresent(K key);

  /** Returns a cached value or atomically computes it when absent */
  V get(K key, Function<? super K, ? extends V> loader);

  /** Returns an immutable snapshot of the requested cached entries */
  Map<K, V> getAllPresent(Iterable<? extends K> keys);

  /** Adds or replaces one cached value */
  void put(K key, V value);

  /** Adds or replaces multiple cached values */
  void putAll(Map<? extends K, ? extends V> values);

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
