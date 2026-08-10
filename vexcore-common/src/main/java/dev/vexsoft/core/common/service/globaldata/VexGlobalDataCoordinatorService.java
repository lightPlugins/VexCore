package dev.vexsoft.core.common.service.globaldata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.globaldata.GlobalDataDefinition;
import dev.vexsoft.core.api.globaldata.GlobalDataKey;
import dev.vexsoft.core.api.globaldata.GlobalDataRegistry;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** Default global-data coordinator backed by revisioned JSON and a bounded Caffeine cache. */
@Dependencies({PlayerDataStoreService.class, CacheService.class})
public final class VexGlobalDataCoordinatorService implements
    GlobalDataCoordinatorService,
    AutoCloseable {

  private static final int MAX_UPDATE_ATTEMPTS = 16;

  private final GlobalDataStore store;
  private final VexAsyncCache<GlobalDataReference, GlobalCacheEntry> cache;
  private final Map<GlobalDataReference, RegisteredGlobalData> registrations =
      new LinkedHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final AutoCloseable invalidationSubscription;

  public VexGlobalDataCoordinatorService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    store = checkedServices.require(PlayerDataStoreService.class).getGlobalStore();
    cache = checkedServices.require(CacheService.class).createAsync(
        "global-data",
        VexCacheOptions.builder()
            .maximumSize(10_000L)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(),
        reference -> store.loadGlobalData(reference.owner(), reference.key())
            .thenApply(GlobalCacheEntry::new)
    );
    invalidationSubscription = store.subscribeGlobalDataChanges(cache::invalidate);
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final GlobalDataDefinition definition
  ) {
    String ownerName = ownerName(owner);
    GlobalDataRegistry registry = key -> registerKey(ownerName, key);
    Objects.requireNonNull(definition, "definition").register(registry);
  }

  @Override
  public <T> CompletableFuture<T> get(
      final ServiceOwner owner,
      final GlobalDataKey<T> key
  ) {
    RegisteredGlobalData registration = requireRegistration(owner, key);
    return cache.get(registration.reference()).thenApply(entry -> deserialize(key, entry));
  }

  @Override
  public <T> CompletableFuture<Void> set(
      final ServiceOwner owner,
      final GlobalDataKey<T> key,
      final T value
  ) {
    RegisteredGlobalData registration = requireRegistration(owner, key);
    String json = serialize(key, value);
    return store.setGlobalData(
        registration.reference().owner(),
        registration.reference().key(),
        json
    ).thenAccept(stored -> cache.put(
        registration.reference(),
        new GlobalCacheEntry(Optional.of(stored))
    ));
  }

  @Override
  public <T> CompletableFuture<T> update(
      final ServiceOwner owner,
      final GlobalDataKey<T> key,
      final UnaryOperator<T> updater
  ) {
    RegisteredGlobalData registration = requireRegistration(owner, key);
    return update(registration, key, Objects.requireNonNull(updater, "updater"), 1);
  }

  @Override
  public CompletableFuture<Boolean> reset(
      final ServiceOwner owner,
      final GlobalDataKey<?> key
  ) {
    RegisteredGlobalData registration = requireRegistration(owner, key);
    return store.resetGlobalData(
        registration.reference().owner(),
        registration.reference().key()
    ).thenApply(removed -> {
      cache.invalidate(registration.reference());
      return removed;
    });
  }

  @Override
  public synchronized void unregister(final ServiceOwner owner) {
    String ownerName = ownerName(owner);
    registrations.entrySet().removeIf(entry -> {
      boolean owned = entry.getKey().owner().equals(ownerName);
      if (owned) {
        cache.invalidate(entry.getKey());
      }
      return owned;
    });
  }

  @Override
  public void close() throws Exception {
    cache.invalidateAll();
    synchronized (this) {
      registrations.clear();
    }
    invalidationSubscription.close();
  }

  private synchronized void registerKey(final String owner, final GlobalDataKey<?> key) {
    GlobalDataKey<?> checkedKey = Objects.requireNonNull(key, "key");
    GlobalDataReference reference = new GlobalDataReference(owner, checkedKey.getName());
    RegisteredGlobalData existing = registrations.putIfAbsent(
        reference,
        new RegisteredGlobalData(reference, checkedKey)
    );
    if (existing != null && existing.key() != checkedKey) {
      throw new IllegalStateException(
          "Global data key is already registered: " + owner + ':' + checkedKey.getName()
      );
    }
  }

  private synchronized RegisteredGlobalData requireRegistration(
      final ServiceOwner owner,
      final GlobalDataKey<?> key
  ) {
    String ownerName = ownerName(owner);
    GlobalDataKey<?> checkedKey = Objects.requireNonNull(key, "key");
    GlobalDataReference reference = new GlobalDataReference(ownerName, checkedKey.getName());
    RegisteredGlobalData registration = registrations.get(reference);
    if (registration == null || registration.key() != checkedKey) {
      throw new IllegalArgumentException(
          "Global data key is not registered for " + ownerName + ": " + checkedKey.getName()
      );
    }
    return registration;
  }

  private <T> CompletableFuture<T> update(
      final RegisteredGlobalData registration,
      final GlobalDataKey<T> key,
      final UnaryOperator<T> updater,
      final int attempt
  ) {
    GlobalDataReference reference = registration.reference();
    return store.loadGlobalData(reference.owner(), reference.key()).thenCompose(current -> {
      GlobalCacheEntry currentEntry = new GlobalCacheEntry(current);
      T updated = Objects.requireNonNull(
          updater.apply(deserialize(key, currentEntry)),
          "Global data updater returned null for " + key.getName()
      );
      String json = serialize(key, updated);
      long revision = current.map(StoredGlobalData::revision).orElse(0L);
      return store.compareAndSetGlobalData(
          reference.owner(),
          reference.key(),
          revision,
          json
      ).thenCompose(stored -> {
        if (stored.isPresent()) {
          cache.put(reference, new GlobalCacheEntry(stored));
          return CompletableFuture.completedFuture(updated);
        }
        cache.invalidate(reference);
        if (attempt >= MAX_UPDATE_ATTEMPTS) {
          return CompletableFuture.failedFuture(new IllegalStateException(
              "Global data changed too frequently to update: "
                  + reference.owner() + ':' + reference.key()
          ));
        }
        return update(registration, key, updater, attempt + 1);
      });
    });
  }

  private <T> T deserialize(final GlobalDataKey<T> key, final GlobalCacheEntry entry) {
    Optional<StoredGlobalData> stored = entry.stored();
    if (stored.isEmpty()) {
      return key.createDefaultValue();
    }
    try {
      return mapper.readValue(stored.orElseThrow().value(), key.getType());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Stored global data has an invalid value for " + key.getName(),
          exception
      );
    }
  }

  private <T> String serialize(final GlobalDataKey<T> key, final T value) {
    T checkedValue = key.getType().cast(Objects.requireNonNull(value, "value"));
    try {
      return mapper.writeValueAsString(checkedValue);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "Unable to serialize global data " + key.getName(),
          exception
      );
    }
  }

  private String ownerName(final ServiceOwner owner) {
    String name = Objects.requireNonNull(owner, "owner")
        .getServiceOwnerName()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!name.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid global data owner: " + name);
    }
    return name;
  }
}
