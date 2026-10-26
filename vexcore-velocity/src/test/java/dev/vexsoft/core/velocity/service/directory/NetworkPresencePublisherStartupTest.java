package dev.vexsoft.core.velocity.service.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.network.NetworkPresenceService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** Exercises production proxy registrations, scheduler startup and owned-service shutdown through Core's registry. */
final class NetworkPresencePublisherStartupTest {

    @Test
    @SuppressWarnings("unchecked")
    void emptyProxyPublishesWithoutCarriersAndShutdownWithdrawsAfterDraining() throws Exception {
        var tick = new AtomicReference<Runnable>();
        var cancelled = new AtomicBoolean();
        var task = proxy(ScheduledTask.class, (object, method, arguments) -> {
            cancelled.set(true);
            return null;
        });
        var builder = proxy(Scheduler.TaskBuilder.class, (object, method, arguments) ->
            method.getName().equals("schedule") ? task : object);
        var scheduler = proxy(Scheduler.class, (object, method, arguments) -> {
            tick.set((Runnable) arguments[1]);
            return builder;
        });
        var proxy = proxy(ProxyServer.class, (object, method, arguments) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getAllPlayers" -> List.of();
            default -> throw new AssertionError(method.getName());
        });
        var owner = new VexCoreVelocityPlugin(proxy, proxy(PluginContainer.class,
            (object, method, arguments) -> null), Path.of("test"), proxy(Logger.class,
            (object, method, arguments) -> null));
        try (var store = new MemoryPlayerDataStore()) {
            store.reconcileProfileDocuments().join();
            var storageClosed = new AtomicBoolean();
            var lastWrite = new AtomicReference<String>();
            var storage = (ProfileStorageService) Proxy.newProxyInstance(ProfileStorageService.class.getClassLoader(),
                new Class<?>[]{ProfileStorageService.class, AutoCloseable.class}, (object, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        storageClosed.set(true);
                        return null;
                    }
                    if (storageClosed.get()) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Storage already closed"));
                    }
                    return switch (method.getName()) {
                        case "load" -> store.loadProfileDocuments((Collection<ProfileDocumentKey>) arguments[0]);
                        case "commit" -> {
                            var changes = (Collection<ProfileDocumentChange>) arguments[0];
                            lastWrite.set(changes.iterator().next().value());
                            yield store.commitProfileDocuments(changes);
                        }
                        case "databaseTime" -> CompletableFuture.completedFuture(1000L);
                        default -> throw new AssertionError(method.getName());
                    };
                });
            var registry = new DefaultServiceRegistry();
            var register = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
                Class.class, VexService.class);
            register.setAccessible(true);
            register.invoke(registry, owner, ProfileStorageService.class, storage);
            var services = registry.scoped(owner);
            VexCoreVelocityPlugin.registerNetworkPresenceServices(services);
            services.registerQueuedServices();
            var publisher = services.require(NetworkPresencePublisherService.class);
            assertInstanceOf(VexNetworkPresencePublisherService.class, publisher);
            var presence = services.require(NetworkPresenceService.class);
            assertThrows(CompletionException.class, () -> presence.snapshot().join());
            publisher.start();
            tick.get().run();
            assertEquals(List.of(), presence.snapshot().join());
            services.unregisterOwnedServices();
            assertTrue(cancelled.get());
            assertTrue(storageClosed.get());
            assertTrue(lastWrite.get().contains("\"expiresAt\":0"));
            assertThrows(CompletionException.class, () -> presence.snapshot().join());
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
