package dev.vexsoft.core.common.service.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.network.NetworkPresenceService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Checks proxy-authored presence and writer fencing through Core's real service registry and document store. */
final class VexNetworkPresenceServiceTest {

    @Test
    void emptyBackendReadsProxyPresenceAndLocalTransfersDoNotMakeAPlayerDisappear() throws Exception {
        try (var fixture = new Fixture()) {
            var writer = fixture.service("proxy");
            var reader = fixture.service("garden");
            assertThrows(CompletionException.class, () -> reader.snapshot().join());
            UUID id = UUID.randomUUID();
            var garden = new NetworkPlayer(id, "Alex", new ServerId("garden-1"));
            writer.publish(List.of(garden)).join();
            assertEquals(List.of(garden), reader.snapshot().join());
            var hub = new NetworkPlayer(id, "Alex", new ServerId("hub"));
            fixture.time.addAndGet(2000);
            writer.publish(List.of(hub)).join();
            assertEquals(List.of(hub), reader.snapshot().join());
            writer.publish(List.of()).join();
            assertEquals(List.of(), reader.snapshot().join());
            fixture.time.addAndGet(15001);
            assertThrows(CompletionException.class, () -> reader.snapshot().join());
        }
    }

    @Test
    void anotherProxyCannotReplaceAFreshWriterAndOldShutdownCannotWithdrawItsReplacement() throws Exception {
        try (var fixture = new Fixture()) {
            var old = fixture.service("old_proxy");
            var replacement = fixture.service("new_proxy");
            old.publish(List.of()).join();
            assertThrows(CompletionException.class, () -> replacement.publish(List.of()).join());
            fixture.time.addAndGet(15001);
            var player = new NetworkPlayer(UUID.randomUUID(), "Sam", new ServerId("hub"));
            replacement.publish(List.of(player)).join();
            assertThrows(CompletionException.class, () -> old.publish(List.of()).join());
            old.withdraw().join();
            assertEquals(List.of(player), replacement.snapshot().join());
            replacement.withdraw().join();
            assertThrows(CompletionException.class, () -> replacement.snapshot().join());
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final MemoryPlayerDataStore store = new MemoryPlayerDataStore();
        private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
        private final AtomicLong time = new AtomicLong(1000);

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            store.reconcileProfileDocuments().join();
            var storage = (ProfileStorageService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProfileStorageService.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "load" -> store.loadProfileDocuments((Collection<ProfileDocumentKey>) arguments[0]);
                    case "commit" -> store.commitProfileDocuments((Collection<ProfileDocumentChange>) arguments[0]);
                    case "databaseTime" -> CompletableFuture.completedFuture(time.get());
                    default -> throw new AssertionError(method.getName());
                });
            var register = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
                Class.class, VexService.class);
            register.setAccessible(true);
            register.invoke(registry, (ServiceOwner) () -> "core", ProfileStorageService.class, storage);
        }

        private NetworkPresenceService service(final String name) {
            var services = registry.scoped(() -> name);
            services.register(NetworkPresenceService.class, VexNetworkPresenceService.class);
            services.registerQueuedServices();
            return services.require(NetworkPresenceService.class);
        }

        @Override
        public void close() {
            store.close();
        }
    }
}
