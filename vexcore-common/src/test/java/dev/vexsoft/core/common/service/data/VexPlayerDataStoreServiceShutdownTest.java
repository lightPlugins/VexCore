package dev.vexsoft.core.common.service.data;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.configuration.YamlVexConfiguration;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
public final class VexPlayerDataStoreServiceShutdownTest {

    @Test
    public void closeWaitsForDrainAndFinishesBackendClosureBeforeReturning(@TempDir final Path directory)
        throws Exception {
        var service = storage(directory);
        var drain = new CompletableFuture<Void>();
        var started = new CompletableFuture<Void>();
        UUID player = UUID.randomUUID();
        service.getPlayerIdentityStore().recordPlayerIdentity(player, "Alex").join();
        service.drainBeforeClose(drain);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closed = CompletableFuture.runAsync(() -> {
                started.complete(null);
                service.close();
            }, executor);
            try {
                started.get(5, TimeUnit.SECONDS);
                assertThrows(TimeoutException.class, () -> closed.get(100, TimeUnit.MILLISECONDS));
                assertTrue(service.getPlayerIdentityStore().findPlayerIdentity(player).join().isPresent());
            } finally {
                drain.complete(null);
            }
            closed.get(5, TimeUnit.SECONDS);
            assertTrue(service.getPlayerIdentityStore().findPlayerIdentity(player).join().isEmpty());
        }
    }

    @Test
    public void failedDrainStillClosesTheBackend(@TempDir final Path directory) {
        var service = storage(directory);
        UUID player = UUID.randomUUID();
        service.getPlayerIdentityStore().recordPlayerIdentity(player, "Alex").join();
        service.drainBeforeClose(CompletableFuture.failedFuture(new IllegalStateException("Checkpoint failed")));
        service.close();
        assertTrue(service.getPlayerIdentityStore().findPlayerIdentity(player).join().isEmpty());
    }

    private static VexPlayerDataStoreService storage(final Path directory) {
        var configuration = new YamlVexConfiguration(directory.resolve("database.yml"));
        configuration.set("storage", "memory");
        ConfigurationService configurations = (ConfigurationService) Proxy.newProxyInstance(
            ConfigurationService.class.getClassLoader(), new Class<?>[]{ConfigurationService.class},
            (instance, method, arguments) -> {
                if (method.getName().equals("load")) {
                    return configuration;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        VexServiceRegistry services = (VexServiceRegistry) Proxy.newProxyInstance(
            VexServiceRegistry.class.getClassLoader(), new Class<?>[]{VexServiceRegistry.class},
            (instance, method, arguments) -> {
                if (method.getName().equals("require") && arguments[0] == ConfigurationService.class) {
                    return configurations;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        return new VexPlayerDataStoreService(services);
    }
}
