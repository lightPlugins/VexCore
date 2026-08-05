package dev.vexsoft.core.cache;

import dev.vexsoft.core.api.service.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Creates and owns bounded local caches for one Vex plugin
 */
public interface CacheService extends VexService {

  /** Creates a synchronous cache using safe default options */
  public default <K, V> VexCache<K, V> create(final String name) {
    return create(name, VexCacheOptions.defaults());
  }

  /** Creates a synchronous cache with the requested options */
  public <K, V> VexCache<K, V> create(String name, VexCacheOptions options);

  /** Creates an asynchronous loading cache using safe default options */
  public default <K, V> VexAsyncCache<K, V> createAsync(
      final String name,
      final Function<? super K, ? extends CompletableFuture<V>> loader
  ) {
    return createAsync(name, VexCacheOptions.defaults(), loader);
  }

  /** Creates an asynchronous cache whose loader returns without blocking the caller */
  public <K, V> VexAsyncCache<K, V> createAsync(
      String name,
      VexCacheOptions options,
      Function<? super K, ? extends CompletableFuture<V>> loader
  );

  /** Destroys a named cache and invalidates all of its entries */
  public void destroy(String name);

  /** Destroys every cache owned by this service */
  public void destroyAll();
}
