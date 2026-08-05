package dev.vexsoft.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.data.storage.PlayerDataStore;
import dev.vexsoft.core.data.storage.PlayerDataStoreService;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class VexPlayerDataCoordinatorServiceTest {

  private static final DataContainerKey<String> PROFILE = DataContainerKey.of(
      "profile",
      String.class,
      () -> "default"
  );

  @Test
  public void sharesConcurrentLoadsForTheSamePlayer() {
    DelayedPlayerDataStore store = new DelayedPlayerDataStore();
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID uniqueId = UUID.randomUUID();

    CompletableFuture<VexPlayer> first = coordinator.load(uniqueId, "Alex");
    CompletableFuture<VexPlayer> second = coordinator.load(uniqueId, "Alex");

    assertEquals(1, store.loads.get());

    store.loaded.complete(Map.of("profile", "\"loaded\""));
    VexPlayer firstPlayer = first.join();
    VexPlayer secondPlayer = second.join();

    assertSame(firstPlayer, secondPlayer);
    assertEquals("loaded", firstPlayer.require(PROFILE));
  }

  private static final class DelayedPlayerDataStore implements PlayerDataStore {

    private final AtomicInteger loads = new AtomicInteger();
    private final CompletableFuture<Map<String, String>> loaded = new CompletableFuture<>();

    @Override
    public CompletableFuture<Void> reconcile(
        final String owner,
        final Collection<DataContainerKey<?>> keys
    ) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> load(
        final String owner,
        final UUID uniqueId,
        final Collection<DataContainerKey<?>> keys
    ) {
      loads.incrementAndGet();
      return loaded;
    }

    @Override
    public CompletableFuture<Void> save(
        final String owner,
        final UUID uniqueId,
        final String playerName,
        final Map<String, String> values
    ) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() { }
  }

  private static final class TestServices implements VexServiceRegistry, ServiceOwner {

    private final CacheService cache;
    private final PlayerDataStoreService store;

    private TestServices(final PlayerDataStore playerDataStore) {
      store = () -> playerDataStore;
      cache = new VexCacheService(this);
    }

    @Override
    public ServiceOwner getOwner() {
      return this;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner owner) {
      return this;
    }

    @Override
    public String getServiceOwnerName() {
      return "VexCoreTest";
    }

    @Override
    public <T extends VexService> void register(
        final Class<T> serviceType,
        final Class<? extends T> implementationType
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerQueuedServices() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      if (serviceType == CacheService.class) {
        return Optional.of(serviceType.cast(cache));
      }
      if (serviceType == PlayerDataStoreService.class) {
        return Optional.of(serviceType.cast(store));
      }
      return Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      return find(serviceType).orElseThrow();
    }

    @Override
    public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAvailable(final Class<? extends VexService> serviceType) {
      return find(serviceType).isPresent();
    }

    @Override
    public void unregister(final Class<? extends VexService> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOwnedServices() {
      throw new UnsupportedOperationException();
    }
  }
}
