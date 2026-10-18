package dev.vexsoft.core.common.service.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Checks that initial configuration IO cannot block registration on a plugin's server thread. */
class VexPublishedConfigurationServiceTest {

    private static final String COMMIT = "a".repeat(40);

    @Test
    void aRenamedSystemCanReadTheLegacySnapshotWithoutWritingEitherKey() throws Exception {
        var fixture = new StoreFixture();
        var legacy = new StoredGlobalData("{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":42}}", 7);
        fixture.values.put("published_items", legacy);
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            service.readiness().join();
            assertEquals(42, service.require(Snapshot.class).value());
            assertEquals(0, service.revision(Snapshot.class));
            assertEquals(List.of("published_items_glint_v1", "published_items"), fixture.reads);
            assertEquals(Map.of("published_items", legacy), fixture.values);
            assertTrue(fixture.writes.isEmpty());
        }
    }

    @Test
    void readinessWaitsForTheLegacyReadAndNotifiesOnlyAfterItIsInstalled() throws Exception {
        var fixture = new StoreFixture();
        fixture.pendingLegacy = new CompletableFuture<>();
        try (var service = service(fixture.store())) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                service.register(Snapshot.class, "items_glint_v1", "items"));
            var notifications = new AtomicInteger();
            service.subscribe(Snapshot.class, ignored -> notifications.incrementAndGet());
            assertFalse(service.readiness().isDone());
            fixture.pendingLegacy.complete(Optional.of(new StoredGlobalData(
                "{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":42}}", 7)));
            service.readiness().join();
            assertEquals(42, service.require(Snapshot.class).value());
            assertEquals(1, notifications.get());
        }
    }

    @Test
    void anExistingNewSnapshotWinsWithoutReadingTheLegacyKey() throws Exception {
        var fixture = new StoreFixture();
        fixture.values.put("published_items_glint_v1", new StoredGlobalData(
            "{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":99}}", 3));
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            service.readiness().join();
            assertEquals(99, service.require(Snapshot.class).value());
            assertEquals(3, service.revision(Snapshot.class));
            assertEquals(List.of("published_items_glint_v1"), fixture.reads);
            assertTrue(fixture.writes.isEmpty());
        }
    }

    @Test
    void theFirstPublishWritesTheNewKeyEvenWhenTheLegacyCommitIsUnchanged() throws Exception {
        var fixture = new StoreFixture();
        var legacy = new StoredGlobalData("{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":42}}", 7);
        fixture.values.put("published_items", legacy);
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            service.readiness().join();
            service.publish(Snapshot.class, new Snapshot(43), COMMIT).join();
            assertEquals(43, service.require(Snapshot.class).value());
            assertEquals(1, service.revision(Snapshot.class));
            assertEquals(legacy, fixture.values.get("published_items"));
            assertEquals(List.of("published_items_glint_v1"), fixture.writes);
        }
    }

    @Test
    void aLateLegacyReadCannotOverwriteAConcurrentPublication() throws Exception {
        var fixture = new StoreFixture();
        fixture.pendingLegacy = new CompletableFuture<>();
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            service.publish(Snapshot.class, new Snapshot(99), COMMIT).join();
            fixture.pendingLegacy.complete(Optional.of(new StoredGlobalData(
                "{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":42}}", 7)));
            service.readiness().join();
            assertEquals(99, service.require(Snapshot.class).value());
            assertEquals(1, service.revision(Snapshot.class));
        }
    }

    @Test
    void invalidNewSnapshotsAreReportedWithoutMaskingThemWithLegacyData() throws Exception {
        var fixture = new StoreFixture();
        fixture.values.put("published_items_glint_v1", new StoredGlobalData("invalid", 3));
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            assertThrows(CompletionException.class, () -> service.readiness().join());
            assertTrue(service.find(Snapshot.class).isEmpty());
            assertEquals(List.of("published_items_glint_v1"), fixture.reads);
        }
    }

    @Test
    void missingLegacySnapshotsStayUnpublishedAndClosingPreventsLateInstallation() throws Exception {
        var fixture = new StoreFixture();
        try (var service = service(fixture.store())) {
            service.register(Snapshot.class, "items_glint_v1", "items");
            service.readiness().join();
            assertTrue(service.find(Snapshot.class).isEmpty());
            assertTrue(fixture.writes.isEmpty());
        }
        fixture.pendingLegacy = new CompletableFuture<>();
        var service = service(fixture.store());
        service.register(Snapshot.class, "items_glint_v1", "items");
        var notifications = new AtomicInteger();
        service.subscribe(Snapshot.class, ignored -> notifications.incrementAndGet());
        service.close();
        fixture.pendingLegacy.complete(Optional.of(new StoredGlobalData(
            "{\"commit\":\"" + COMMIT + "\",\"data\":{\"value\":42}}", 7)));
        assertEquals(0, notifications.get());
        assertFalse(service.isRegistered(Snapshot.class));
    }

    @Test
    void registrationReturnsBeforeTheDatabaseAndReadinessAwaitsTheSnapshot() throws Exception {
        var read = new CompletableFuture<Optional<StoredGlobalData>>();
        try (var service = service(read)) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> service.register(Snapshot.class, "profiles"));
            var notifications = new AtomicInteger();
            service.subscribe(Snapshot.class, value -> notifications.incrementAndGet());
            assertFalse(service.readiness().isDone());
            assertTrue(service.find(Snapshot.class).isEmpty());
            read.complete(Optional.of(new StoredGlobalData("{\"commit\":\"abc\",\"data\":{\"value\":42}}", 1)));
            service.readiness().join();
            assertEquals(42, service.require(Snapshot.class).value());
            assertEquals(1, notifications.get());
        }
    }

    @Test
    void failedInitializationFailsReadinessWithoutPublishingAPartialValue() throws Exception {
        var read = new CompletableFuture<Optional<StoredGlobalData>>();
        try (var service = service(read)) {
            service.register(Snapshot.class, "profiles");
            var readiness = service.readiness();
            read.completeExceptionally(new IllegalStateException("Database unavailable"));
            assertTrue(readiness.isCompletedExceptionally());
            assertTrue(service.find(Snapshot.class).isEmpty());
        }
    }

    @Test
    void closingBeforeTheInitialReadPreventsLatePublication() throws Exception {
        var read = new CompletableFuture<Optional<StoredGlobalData>>();
        var service = service(read);
        service.register(Snapshot.class, "profiles");
        var notifications = new AtomicInteger();
        service.subscribe(Snapshot.class, value -> notifications.incrementAndGet());
        service.close();
        read.complete(Optional.of(new StoredGlobalData("{\"commit\":\"abc\",\"data\":{\"value\":42}}", 1)));
        assertEquals(0, notifications.get());
        assertFalse(service.isRegistered(Snapshot.class));
    }

    private static VexPublishedConfigurationService service(
        final CompletableFuture<Optional<StoredGlobalData>> read
    ) {
        var store = (GlobalDataStore) Proxy.newProxyInstance(GlobalDataStore.class.getClassLoader(),
            new Class<?>[]{GlobalDataStore.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "loadGlobalData" -> read;
                case "subscribeGlobalDataChanges" -> (AutoCloseable) () -> {
                };
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return service(store);
    }

    private static VexPublishedConfigurationService service(final GlobalDataStore store) {
        var backend = (PlayerDataStoreService) Proxy.newProxyInstance(PlayerDataStoreService.class.getClassLoader(),
            new Class<?>[]{PlayerDataStoreService.class}, (proxy, method, arguments) -> store);
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (proxy, method, arguments) ->
                method.getName().equals("getOwner") ? (ServiceOwner) () -> "test" : backend);
        return new VexPublishedConfigurationService(registry);
    }

    private static final class StoreFixture {

        private final Map<String, StoredGlobalData> values = new HashMap<>();
        private final List<String> reads = new ArrayList<>();
        private final List<String> writes = new ArrayList<>();
        private CompletableFuture<Optional<StoredGlobalData>> pendingLegacy;

        private GlobalDataStore store() {
            return (GlobalDataStore) Proxy.newProxyInstance(GlobalDataStore.class.getClassLoader(),
                new Class<?>[]{GlobalDataStore.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "loadGlobalData" -> {
                        String key = (String) arguments[1];
                        reads.add(key);
                        yield key.equals("published_items") && pendingLegacy != null ? pendingLegacy
                            : CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
                    }
                    case "compareAndSetGlobalData" -> {
                        String key = (String) arguments[1];
                        long revision = values.containsKey(key) ? values.get(key).revision() : 0;
                        if (revision != (long) arguments[2]) {
                            yield CompletableFuture.completedFuture(Optional.empty());
                        }
                        var stored = new StoredGlobalData((String) arguments[3], revision + 1);
                        writes.add(key);
                        values.put(key, stored);
                        yield CompletableFuture.completedFuture(Optional.of(stored));
                    }
                    case "subscribeGlobalDataChanges" -> (AutoCloseable) () -> {
                    };
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
    }

    public record Snapshot(int value) {
    }
}
