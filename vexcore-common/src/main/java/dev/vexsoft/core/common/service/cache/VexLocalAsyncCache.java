package dev.vexsoft.core.common.service.cache;

import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheStats;

import com.github.benmanes.caffeine.cache.AsyncCache;
import dev.vexsoft.core.common.cache.internal.ManagedVexCache;
import dev.vexsoft.core.common.cache.internal.VexCacheStatistics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.Getter;

final class VexLocalAsyncCache<K, V> implements VexAsyncCache<K, V>, ManagedVexCache {

  @Getter
  private final String name;
  private final AsyncCache<K, V> cache;
  private final Function<? super K, ? extends CompletableFuture<V>> loader;

  VexLocalAsyncCache(
      final String name,
      final AsyncCache<K, V> cache,
      final Function<? super K, ? extends CompletableFuture<V>> loader
  ) {
    this.name = Objects.requireNonNull(name, "name");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.loader = Objects.requireNonNull(loader, "loader");
  }

  @Override
  public Optional<CompletableFuture<V>> getIfPresent(final K key) {
    return Optional.ofNullable(cache.getIfPresent(Objects.requireNonNull(key, "key")));
  }

  @Override
  public CompletableFuture<V> get(final K key) {
    return cache.get(Objects.requireNonNull(key, "key"), (cacheKey, executor) -> load(cacheKey));
  }

  @Override
  public void put(final K key, final V value) {
    putFuture(key, CompletableFuture.completedFuture(Objects.requireNonNull(value, "value")));
  }

  @Override
  public void putFuture(final K key, final CompletableFuture<V> value) {
    CompletableFuture<V> checkedValue = Objects.requireNonNull(value, "value")
        .thenApply(result -> Objects.requireNonNull(result, "completed value"));
    cache.put(Objects.requireNonNull(key, "key"), checkedValue);
  }

  @Override
  public Map<K, CompletableFuture<V>> getAllPresent(final Iterable<? extends K> keys) {
    Map<K, CompletableFuture<V>> values = new LinkedHashMap<>();
    for (K key : Objects.requireNonNull(keys, "keys")) {
      CompletableFuture<V> value = cache.getIfPresent(Objects.requireNonNull(key, "key"));
      if (value != null) {
        values.put(key, value);
      }
    }
    return Map.copyOf(values);
  }

  @Override
  public void invalidate(final K key) {
    cache.synchronous().invalidate(Objects.requireNonNull(key, "key"));
  }

  @Override
  public void invalidateAll(final Iterable<? extends K> keys) {
    cache.synchronous().invalidateAll(Objects.requireNonNull(keys, "keys"));
  }

  @Override
  public void invalidateAll() {
    cache.synchronous().invalidateAll();
  }

  @Override
  public long getEstimatedSize() {
    return cache.synchronous().estimatedSize();
  }

  @Override
  public VexCacheStats getStats() {
    return VexCacheStatistics.snapshot(cache.synchronous().stats());
  }

  @Override
  public void cleanUp() {
    cache.synchronous().cleanUp();
  }

  private CompletableFuture<V> load(final K key) {
    try {
      return Objects.requireNonNull(loader.apply(key), "loader future")
          .thenApply(value -> Objects.requireNonNull(value, "loaded value"));
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }
}
