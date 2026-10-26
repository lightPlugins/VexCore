package dev.vexsoft.core.velocity.service.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.network.BackendShutdown;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.common.service.configuration.VexConfigurationService;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import net.kyori.adventure.text.Component;

/** Exercises the real awaiting event gate without sockets or blocking event threads. */
class VexServerTransferServiceTest {

    @Test
    void productionRegistrationLoadsProxyConfigAndRetiresItsEventListeners(@TempDir final Path directory)
        throws Exception {
        var listeners = new ArrayList<Object>();
        var eventManager = proxy(EventManager.class, (object, method, arguments) -> {
            if (method.getName().equals("register")) {
                listeners.add(arguments[1]);
            } else if (method.getName().equals("unregisterListener")) {
                assertTrue(listeners.remove(arguments[1]));
            }
            return null;
        });
        var proxyServer = proxy(ProxyServer.class, (object, method, arguments) -> eventManager);
        var owner = new VexCoreVelocityPlugin(proxyServer, proxy(PluginContainer.class,
            (object, method, arguments) -> null), directory.resolve("plugins/vexcore"),
            proxy(Logger.class, (object, method, arguments) -> null));
        var registry = new DefaultServiceRegistry();
        var register = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
            Class.class, VexService.class);
        register.setAccessible(true);
        for (Class<?> type : List.of(MessagingService.class, AdmissionService.class)) {
            register.invoke(registry, owner, type, proxy(type, (object, method, arguments) -> {
                throw new AssertionError(method.getName());
            }));
        }
        var services = registry.scoped(owner);
        services.register(ConfigurationService.class, VexConfigurationService.class);
        VexCoreVelocityPlugin.registerServerTransferServices(services);
        services.registerQueuedServices();
        var service = services.require(ServerTransferService.class);
        assertInstanceOf(VexServerTransferService.class, service);
        assertTrue(Files.readString(owner.getConfigurationDirectory().resolve("server-transfer.yml"))
            .contains("shutdown-fallback-server: lobby"));
        service.start();
        assertEquals(List.of(service), listeners);
        services.unregisterOwnedServices();
        assertTrue(listeners.isEmpty());
    }

    @Test
    void controlledShutdownRedirectsToTheLobbyAfterTheSourceConnectionIsGoneWithoutAnotherSaveRequest() {
        try (var fixture = new Fixture()) {
            var kicked = fixture.kicked(fixture.source, BackendShutdown.reason(), false);
            fixture.service.onKicked(kicked);
            assertEquals(fixture.lobby,
                assertInstanceOf(KickedFromServerEvent.RedirectPlayer.class, kicked.getResult()).getServer());
            fixture.current = null;
            var redirect = new ServerPreConnectEvent(fixture.player, fixture.lobby, fixture.source);
            assertNull(fixture.service.onPreConnect(redirect));
            assertEquals(fixture.lobby, redirect.getResult().getServer().orElseThrow());
            assertTrue(fixture.sent.isEmpty());

            var replay = new ServerPreConnectEvent(fixture.player, fixture.lobby, fixture.source);
            fixture.service.onPreConnect(replay);
            assertTrue(replay.getResult().getServer().isEmpty());
        }
    }

    @Test
    void ordinaryKicksLobbyShutdownAndFailedConnectionAttemptsAreNotRedirected() {
        try (var fixture = new Fixture()) {
            for (var event : List.of(fixture.kicked(fixture.source, Component.text("Kicked by an admin"), false),
                fixture.kicked(fixture.lobby, BackendShutdown.reason(), false),
                fixture.kicked(fixture.source, BackendShutdown.reason(), true))) {
                fixture.service.onKicked(event);
                assertInstanceOf(KickedFromServerEvent.DisconnectPlayer.class, event.getResult());
            }
        }
    }

    @Test
    void fallbackCannotBypassPreparationForAnotherTargetOrANewerPlayerConnection() {
        try (var fixture = new Fixture()) {
            fixture.service.onKicked(fixture.kicked(fixture.source, BackendShutdown.reason(), false));
            fixture.current = null;
            var wrongTarget = new ServerPreConnectEvent(fixture.player, fixture.target, fixture.source);
            fixture.service.onPreConnect(wrongTarget);
            assertTrue(wrongTarget.getResult().getServer().isEmpty());

            fixture.service.onKicked(fixture.kicked(fixture.source, BackendShutdown.reason(), false));
            var newer = proxy(Player.class, (object, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> fixture.playerId;
                case "getCurrentServer" -> Optional.empty();
                default -> null;
            });
            var replay = new ServerPreConnectEvent(newer, fixture.lobby, fixture.source);
            fixture.service.onPreConnect(replay);
            assertTrue(replay.getResult().getServer().isEmpty());
        }
    }

    @Test
    void fallbackDiscardsPendingTransferWithoutTryingToReactivateTheStoppingSource() {
        try (var fixture = new Fixture()) {
            var original = fixture.event();
            var resumed = execute(fixture.service.onPreConnect(original));
            fixture.service.onKicked(fixture.kicked(fixture.source, BackendShutdown.reason(), false));
            assertTrue(resumed.isDone());
            assertTrue(original.getResult().getServer().isEmpty());
            assertFalse(fixture.sent.stream().anyMatch(TeleportAbort.class::isInstance));
            fixture.current = null;
            var redirect = new ServerPreConnectEvent(fixture.player, fixture.lobby, fixture.source);
            fixture.service.onPreConnect(redirect);
            assertEquals(fixture.lobby, redirect.getResult().getServer().orElseThrow());
        }
    }

    @Test
    void missingLobbyAndClosedProxyServiceLeaveTheOriginalDisconnectResult() {
        try (var fixture = new Fixture()) {
            fixture.lobbyAvailable = false;
            var missing = fixture.kicked(fixture.source, BackendShutdown.reason(), false);
            fixture.service.onKicked(missing);
            assertInstanceOf(KickedFromServerEvent.DisconnectPlayer.class, missing.getResult());
            fixture.lobbyAvailable = true;
            fixture.service.close();
            var stopped = fixture.kicked(fixture.source, BackendShutdown.reason(), false);
            fixture.service.onKicked(stopped);
            assertInstanceOf(KickedFromServerEvent.DisconnectPlayer.class, stopped.getResult());
        }
    }

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
        private final RegisteredServer lobby = server("lobby-6bso45");
        private boolean lobbyAvailable = true;
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
                    case "getServer" -> arguments[0].equals("lobby-6bso45")
                        ? lobbyAvailable ? Optional.of(lobby) : Optional.empty() : Optional.of(target);
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
            var config = proxy(VexConfiguration.class, (object, method, arguments) -> "lobby-6bso45");
            var configurations = proxy(ConfigurationService.class, (object, method, arguments) -> config);
            var registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                method.getName().equals("getOwner") ? owner
                    : arguments[0] == AdmissionService.class ? admissions
                    : arguments[0] == ConfigurationService.class ? configurations : messages);
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

        private KickedFromServerEvent kicked(final RegisteredServer server, final Component reason,
            final boolean duringConnect) {
            return new KickedFromServerEvent(player, server, reason, duringConnect,
                KickedFromServerEvent.DisconnectPlayer.create(reason));
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
