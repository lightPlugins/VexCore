package dev.vexsoft.core.velocity.service.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.network.admission.AdmissionReference;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.world.WorldKey;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparationResult;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** Exercises the real awaiting event gate without sockets or blocking event threads. */
class VexServerTransferServiceTest {

    @Test
    void routedSwitchAwaitsAtomicAdmissionAndRejectsAnExpiredReservation() {
        try (var fixture = new Fixture()) {
            fixture.validation = new CompletableFuture<>();
            fixture.routed();
            var event = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(event));
            var preparation = fixture.preparation();
            assertTrue(preparation.options().admission() != null);
            fixture.service.prepared(new TeleportPreparationResult(preparation.requestId(), true), "isles-1");
            assertFalse(resumed.isDone());
            fixture.validation.complete(false);
            assertTrue(resumed.isDone());
            assertTrue(event.getResult().getServer().isEmpty());
            assertTrue(fixture.sent.stream().anyMatch(TeleportAbort.class::isInstance));
        }
    }

    @Test
    void ordinaryProxySwitchWaitsForTheCorrectSourceAcknowledgement() {
        try (var fixture = new Fixture()) {
            var event = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(event));
            assertFalse(resumed.isDone());
            var request = fixture.preparation();
            fixture.service.prepared(new TeleportPreparationResult(request.requestId(), true), "other");
            assertFalse(resumed.isDone());
            fixture.service.prepared(new TeleportPreparationResult(request.requestId(), true), "isles-1");
            assertTrue(resumed.isDone());
            assertEquals(fixture.target, event.getResult().getServer().orElseThrow());
        }
    }

    @Test
    void saveFailureRejectsTheSwitchAndRequestsSourceRecovery() {
        try (var fixture = new Fixture()) {
            var event = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(event));
            fixture.service.prepared(new TeleportPreparationResult(fixture.preparation().requestId(), false), "isles-1");
            assertTrue(resumed.isDone());
            assertTrue(event.getResult().getServer().isEmpty());
            assertTrue(fixture.sent.stream().anyMatch(TeleportAbort.class::isInstance));
        }
    }

    @Test
    void anAcknowledgementCannotMoveANewerConnectionOfTheSamePlayer() {
        try (var fixture = new Fixture()) {
            var event = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(event));
            fixture.current = fixture.connection();
            fixture.service.prepared(new TeleportPreparationResult(fixture.preparation().requestId(), true), "isles-1");
            assertTrue(resumed.isDone());
            assertTrue(event.getResult().getServer().isEmpty());
        }
    }

    @Test
    void overlappingSwitchesCannotRetireTheSameSourceTwice() {
        try (var fixture = new Fixture()) {
            var first = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(first));
            var second = fixture.event();
            fixture.service.onPreConnect(second);
            assertTrue(second.getResult().getServer().isEmpty());
            assertFalse(resumed.isDone());
            assertEquals(1, fixture.sent.stream().filter(TeleportPreparation.class::isInstance).count());
        }
    }

    private static CompletableFuture<Void> execute(final EventTask task) {
        var result = new CompletableFuture<Void>();
        task.execute(new Continuation() {
            @Override
            public void resume() {
                result.complete(null);
            }

            @Override
            public void resumeWithException(final Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static final class Fixture implements AutoCloseable {

        private final UUID playerId = UUID.randomUUID();
        private final List<Object> sent = new ArrayList<>();
        private final RegisteredServer source = server("isles-1");
        private final RegisteredServer target = server("isles-2");
        private ServerConnection current = connection();
        private final Player player = proxy(Player.class, (object, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getCurrentServer" -> Optional.ofNullable(current);
            case "isActive" -> true;
            case "createConnectionRequest" -> proxy(ConnectionRequestBuilder.class,
                (builder, operation, values) -> new CompletableFuture<>());
            default -> null;
        });
        private final VexServerTransferService service;
        private CompletableFuture<Boolean> validation = CompletableFuture.completedFuture(true);

        private Fixture() {
            var eventManager = proxy(EventManager.class, (object, method, arguments) -> null);
            var proxyServer = proxy(ProxyServer.class, (object, method, arguments) ->
                switch (method.getName()) {
                    case "getEventManager" -> eventManager;
                    case "getPlayer" -> Optional.of(player);
                    case "getServer" -> Optional.of(target);
                    default -> null;
                });
            var logger = proxy(Logger.class, (object, method, arguments) -> null);
            var owner = new VexCoreVelocityPlugin(proxyServer, proxy(PluginContainer.class,
                (object, method, arguments) -> null), Path.of("test"), logger);
            var messages = proxy(MessagingService.class, (object, method, arguments) -> {
                if (method.getName().equals("send")) {
                    sent.add(arguments[2]);
                }
                return DeliveryResult.QUEUED;
            });
            var admissions = proxy(AdmissionService.class,
                (object, method, arguments) -> validation);
            var registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                method.getName().equals("getOwner") ? owner
                    : arguments[0] == AdmissionService.class ? admissions : messages);
            service = new VexServerTransferService(registry);
            service.start();
        }

        private void routed() {
            var destination = new ServerPosition(new ServerId("isles-2"),
                WorldKey.parse("test:spawn"), 0, 80, 0, 0, 0);
            var reference = new AdmissionReference(UUID.randomUUID(), playerId,
                "isles", destination.server(), UUID.randomUUID());
            service.transfer(new TeleportTransferRequest(UUID.randomUUID(),
                playerId, destination, "isles-1", new TransferOptions(reference, null, false)), "isles-1");
        }

        private ServerPreConnectEvent event() {
            return new ServerPreConnectEvent(player, target, source);
        }

        private TeleportPreparation preparation() {
            return (TeleportPreparation) sent.stream().filter(TeleportPreparation.class::isInstance).findFirst().orElseThrow();
        }

        private ServerConnection connection() {
            return proxy(ServerConnection.class, (object, method, arguments) -> switch (method.getName()) {
                case "getServer" -> source;
                case "getServerInfo" -> source.getServerInfo();
                default -> null;
            });
        }

        @Override
        public void close() {
            service.close();
        }
    }

    private static RegisteredServer server(final String name) {
        var info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return proxy(RegisteredServer.class, (object, method, arguments) -> method.getName().equals("getServerInfo")
            ? info : null);
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, arguments) -> {
            if (method.getName().equals("equals")) {
                return object == arguments[0];
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(object);
            }
            return handler.invoke(object, method, arguments);
        }));
    }
}
