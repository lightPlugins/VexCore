package dev.vexsoft.core.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.internal.ManagedVexCache;
import dev.vexsoft.core.cache.internal.VexCaffeineFactory;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

@Dependencies
public final class VexCacheService implements CacheService, AutoCloseable {

  private static final Pattern CACHE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  private final ServiceOwner owner;
  private final Map<String, ManagedVexCache> caches = new ConcurrentHashMap<>();

  public VexCacheService(final VexServiceRegistry services) {
    owner = Objects.requireNonNull(services, "services").getOwner();
  }

  @Override
  public <K, V> VexCache<K, V> create(
      final String name,
      final VexCacheOptions options
  ) {
    String checkedName = validateName(name);
    Cache<K, V> cache = VexCaffeineFactory.create(options).build();
    VexLocalCache<K, V> created = new VexLocalCache<>(checkedName, cache);
    register(checkedName, created);
    return created;
  }

  @Override
  public <K, V> VexAsyncCache<K, V> createAsync(
      final String name,
      final VexCacheOptions options,
      final Function<? super K, ? extends CompletableFuture<V>> loader
  ) {
    String checkedName = validateName(name);
    AsyncCache<K, V> cache = VexCaffeineFactory.create(options).buildAsync();
    VexLocalAsyncCache<K, V> created = new VexLocalAsyncCache<>(
        checkedName,
        cache,
        Objects.requireNonNull(loader, "loader")
    );
    register(checkedName, created);
    return created;
  }

  @Override
  public void destroy(final String name) {
    ManagedVexCache cache = caches.remove(normalizeName(name));
    if (cache != null) {
      clear(cache);
    }
  }

  @Override
  public void destroyAll() {
    for (ManagedVexCache cache : new ArrayList<>(caches.values())) {
      clear(cache);
    }
    caches.clear();
  }

  @Override
  public void close() {
    destroyAll();
  }

  private void register(final String name, final ManagedVexCache cache) {
    ManagedVexCache existing = caches.putIfAbsent(name, cache);
    if (existing != null) {
      clear(cache);
      throw new IllegalStateException(
          "Cache is already registered for " + owner.getServiceOwnerName() + ": " + name
      );
    }
  }

  private String validateName(final String name) {
    String normalized = normalizeName(name);
    if (!CACHE_NAME.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid cache name: " + name);
    }
    return normalized;
  }

  private String normalizeName(final String name) {
    return Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
  }

  private void clear(final ManagedVexCache cache) {
    cache.invalidateAll();
    cache.cleanUp();
  }
}
