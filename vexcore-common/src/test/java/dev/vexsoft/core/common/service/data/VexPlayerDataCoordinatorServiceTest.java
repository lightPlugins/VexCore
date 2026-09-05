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

  @Test
  public void unloadedOwnerCanStillBeSavedAndRecovered(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory
  ) throws Exception {
    MemoryPlayerDataStore store = new MemoryPlayerDataStore();
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    DataContainerKey<UnloadSensitiveData> key = DataContainerKey.of(
        "unload_sensitive", UnloadSensitiveData.class, UnloadSensitiveData::new);
    coordinator.register(services.getOwner(), registry -> registry.register(key));
    VexPlayer player = coordinator.create(UUID.randomUUID(), "Alex");
    coordinator.prepareUnload(services.getOwner());
    player.require(key).unavailable = true;
    try {
      coordinator.exportRecovery(directory);
      var recovered = new com.fasterxml.jackson.databind.ObjectMapper()
          .readTree(directory.resolve(player.getUniqueId() + ".json").toFile());
      assertTrue(recovered.toString().contains("\"value\":42"));
      coordinator.saveAll().join();
      assertTrue(player.getDirtyKeys().isEmpty());
      var saved = store.load(services.getOwner().getServiceOwnerName().toLowerCase(java.util.Locale.ROOT)
          .replace('-', '_'), player.getUniqueId(), List.of(key)).join();
      assertEquals("{\"value\":42}", saved.get(key.getName()));
    } finally {
      coordinator.close();
    }
  }

  public static final class UnloadSensitiveData {
    private boolean unavailable;

    public int getValue() {
      if (unavailable) throw new IllegalStateException("Plugin classloader is closed");
      return 42;
    }
  }

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
  public void reconnectWaitsUntilRetiringSessionIsRemoved() {
    DelayedPlayerDataStore store = new DelayedPlayerDataStore();
    store.loaded.complete(Map.of());
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID id = UUID.randomUUID();
    VexPlayer original = coordinator.load(id, "Alex").join();
    store.saved = new CompletableFuture<>();
    CompletableFuture<Void> quit = coordinator.saveAndRemove(id);
    CompletableFuture<VexPlayer> reconnect = coordinator.load(id, "Alex");
    org.junit.jupiter.api.Assertions.assertFalse(reconnect.isDone());
    store.saved.complete(null);
    quit.join();
    org.junit.jupiter.api.Assertions.assertNotSame(original, reconnect.join());
    assertSame(reconnect.join(), coordinator.find(id).orElseThrow());
  }

  @Test
  public void failedQuitSaveRetainsDirtyDataForRetry() {
    DelayedPlayerDataStore store = new DelayedPlayerDataStore();
    store.loaded.complete(Map.of());
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID id = UUID.randomUUID();
    VexPlayer original = coordinator.load(id, "Alex").join();
    store.saved = CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
    org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
        () -> coordinator.saveAndRemove(id).join());
    assertSame(original, coordinator.find(id).orElseThrow());
    assertTrue(original.getDirtyKeys().contains(PROFILE));
    store.saved = CompletableFuture.completedFuture(null);
    coordinator.saveAndRemove(id).join();
    assertTrue(coordinator.find(id).isEmpty());
  }

  @Test
  public void failedSaveCanBeExportedBeforeShutdown(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
      throws Exception {
    DelayedPlayerDataStore store = new DelayedPlayerDataStore();
    store.loaded.complete(Map.of());
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID id = UUID.randomUUID();
    coordinator.load(id, "Alex").join();
    store.saved = CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
    org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
        () -> coordinator.saveAndRemove(id).join());
    coordinator.exportRecovery(directory);
    var document = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(directory.resolve(id + ".json").toFile());
    assertEquals("Alex", document.get("name").asText());
    assertEquals("default", document.at("/owners/vexcoretest/profile").asText());
    coordinator.close();
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
  public void savesDirtyDataWithoutRemovingTheLoadedPlayer() {
    MemoryPlayerDataStore store = new MemoryPlayerDataStore();
    TestServices services = new TestServices(store);
    VexPlayerDataCoordinatorService coordinator = new VexPlayerDataCoordinatorService(services);
    coordinator.register(services.getOwner(), registry -> registry.register(PROFILE));
    UUID uniqueId = UUID.randomUUID();
    store.save("vexcoretest", uniqueId, "Alex", Map.of("profile", "\"custom\"")).join();
    VexPlayer player = coordinator.load(uniqueId, "Alex").join();

    player.reset(PROFILE);
    coordinator.save(uniqueId).join();

    assertSame(player, coordinator.find(uniqueId).orElseThrow());
    assertEquals("\"default\"", store.load(
        "vexcoretest",
        uniqueId,
        List.of(PROFILE)
    ).join().get("profile"));
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
    private CompletableFuture<Void> saved = CompletableFuture.completedFuture(null);

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
      return saved;
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
