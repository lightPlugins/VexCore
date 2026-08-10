package dev.vexsoft.core.common.service.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import java.util.Collection;
import java.util.List;
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

  @Test
  public void installsAndRemovesDenseFeatureContainersForLoadedPlayers() {
    DelayedPlayerDataStore store = new DelayedPlayerDataStore();
    store.loaded.complete(Map.of());
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.registerContainer(
        services.getOwner(),
        TestContainer.class,
        ignored -> new TestContainer()
    );

    VexPlayer player = coordinator.load(UUID.randomUUID(), "Alex").join();
    TestContainer container = player.getContainer(TestContainer.class);

    coordinator.unregisterContainers(services.getOwner());

    assertTrue(container.closed);
    assertTrue(player.findContainer(TestContainer.class).isEmpty());
  }

  @Test
  public void resetsLoadedAndOfflineContainersThroughStableContainerIds() {
    MemoryPlayerDataStore store = new MemoryPlayerDataStore();
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID loadedId = UUID.randomUUID();
    UUID offlineId = UUID.randomUUID();
    store.save("vexcoretest", loadedId, "Alex", Map.of("profile", "\"custom\"")).join();
    store.save("vexcoretest", offlineId, "Steve", Map.of("profile", "\"offline\"")).join();
    VexPlayer loaded = coordinator.load(loadedId, "Alex").join();

    coordinator.resetPlayerContainer(loadedId, "profile").join();
    assertEquals("default", loaded.require(PROFILE));
    assertEquals("\"default\"", store.load(
        "vexcoretest",
        loadedId,
        List.of(PROFILE)
    ).join().get("profile"));

    coordinator.resetGlobalContainer("profile").join();
    assertTrue(store.load("vexcoretest", offlineId, List.of(PROFILE)).join().isEmpty());
    assertEquals(offlineId, coordinator.resolveUniqueId("Steve").join().orElseThrow());
  }

  private static final class TestContainer implements PlayerContainer {

    private boolean closed;

    @Override
    public void close() {
      closed = true;
    }
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
      return loaded.thenApply(Map::copyOf);
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
      store = new PlayerDataStoreService() {
        @Override
        public PlayerDataStore getStore() {
          return playerDataStore;
        }

        @Override
        public dev.vexsoft.core.common.data.global.GlobalDataStore getGlobalStore() {
          throw new UnsupportedOperationException();
        }

        @Override
        public dev.vexsoft.core.common.data.identity.PlayerIdentityStore getPlayerIdentityStore() {
          throw new UnsupportedOperationException();
        }
      };
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
