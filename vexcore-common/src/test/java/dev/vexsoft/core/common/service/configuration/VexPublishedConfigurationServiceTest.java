package dev.vexsoft.core.common.service.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;

/** Checks that initial configuration IO cannot block registration on a plugin's server thread. */
class VexPublishedConfigurationServiceTest {

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
        var backend = (PlayerDataStoreService) Proxy.newProxyInstance(PlayerDataStoreService.class.getClassLoader(),
            new Class<?>[]{PlayerDataStoreService.class}, (proxy, method, arguments) -> store);
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (proxy, method, arguments) ->
                method.getName().equals("getOwner") ? (ServiceOwner) () -> "test" : backend);
        return new VexPublishedConfigurationService(registry);
    }

    public record Snapshot(int value) {
    }
}
