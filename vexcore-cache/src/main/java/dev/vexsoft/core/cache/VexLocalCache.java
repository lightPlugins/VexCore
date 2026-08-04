package dev.vexsoft.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import dev.vexsoft.core.cache.internal.ManagedVexCache;
import dev.vexsoft.core.cache.internal.VexCacheStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.Getter;

final class VexLocalCache<K, V> implements VexCache<K, V>, ManagedVexCache {

  @Getter
  private final String name;
  private final Cache<K, V> cache;

  VexLocalCache(final String name, final Cache<K, V> cache) {
    this.name = Objects.requireNonNull(name, "name");
    this.cache = Objects.requireNonNull(cache, "cache");
  }

  @Override
  public Optional<V> getIfPresent(final K key) {
    return Optional.ofNullable(cache.getIfPresent(Objects.requireNonNull(key, "key")));
  }

  @Override
  public V get(final K key, final Function<? super K, ? extends V> loader) {
    Function<? super K, ? extends V> checkedLoader = Objects.requireNonNull(loader, "loader");
    return cache.get(
        Objects.requireNonNull(key, "key"),
        cacheKey -> Objects.requireNonNull(checkedLoader.apply(cacheKey), "loaded value")
    );
  }

  @Override
  public Map<K, V> getAllPresent(final Iterable<? extends K> keys) {
    return Map.copyOf(cache.getAllPresent(Objects.requireNonNull(keys, "keys")));
  }

  @Override
  public void put(final K key, final V value) {
    cache.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> values) {
    Objects.requireNonNull(values, "values").forEach(this::put);
  }

  @Override
  public void invalidate(final K key) {
    cache.invalidate(Objects.requireNonNull(key, "key"));
  }

  @Override
  public void invalidateAll(final Iterable<? extends K> keys) {
    cache.invalidateAll(Objects.requireNonNull(keys, "keys"));
  }

  @Override
  public void invalidateAll() {
    cache.invalidateAll();
  }

  @Override
  public long getEstimatedSize() {
    return cache.estimatedSize();
  }

  @Override
  public VexCacheStats getStats() {
    return VexCacheStatistics.snapshot(cache.stats());
  }

  @Override
  public void cleanUp() {
    cache.cleanUp();
  }
}
