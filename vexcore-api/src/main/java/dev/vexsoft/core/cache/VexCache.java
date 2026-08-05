package dev.vexsoft.core.cache;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a bounded synchronous cache owned by one Vex plugin
 */
public interface VexCache<K, V> {

  /** Returns the owner-local name of this cache */
  public String getName();

  /** Returns a cached value without triggering a load */
  public Optional<V> getIfPresent(K key);

  /** Returns a cached value or atomically computes it when absent */
  public V get(K key, Function<? super K, ? extends V> loader);

  /** Returns an immutable snapshot of the requested cached entries */
  public Map<K, V> getAllPresent(Iterable<? extends K> keys);

  /** Adds or replaces one cached value */
  public void put(K key, V value);

  /** Adds or replaces multiple cached values */
  public void putAll(Map<? extends K, ? extends V> values);

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
