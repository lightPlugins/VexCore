package dev.vexsoft.core.velocity.service.teleport;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparationResult;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;

/** Suspends Velocity event processing, never its threads, while the source drains saves and transactions. */
@Dependencies({MessagingService.class, AdmissionService.class})
public final class VexServerTransferService implements ServerTransferService, AutoCloseable {

    private final VexCoreVelocityPlugin plugin;
    private final ProxyServer proxy;
    private final MessagingService messages;
    private final AdmissionService admissions;
    private final Map<UUID, Pending> players = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> requests = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();

    public VexServerTransferService(final VexServiceRegistry services) {
        if (!(services.getOwner() instanceof VexCoreVelocityPlugin owner)) {
            throw new IllegalArgumentException("Server transfers require the Core proxy owner");
        }
        plugin = owner;
        proxy = plugin.getProxyServer();
        messages = services.require(MessagingService.class);
        admissions = services.require(AdmissionService.class);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            proxy.getEventManager().register(plugin, this);
        }
    }

    @Override
    public void transfer(final TeleportTransferRequest request, final String sourceServer) {
        Pending existing = requests.get(request.requestId());
        if (existing != null && existing.player.getUniqueId().equals(request.playerId())
            && existing.source.getServerInfo().getName().equals(sourceServer)
            && Objects.equals(existing.destination, request.destination())
            && existing.options.equals(request.options())) {
            return;
        }
        Player player = proxy.getPlayer(request.playerId()).orElse(null);
        ServerConnection source = player == null ? null : player.getCurrentServer().orElse(null);
        RegisteredServer target = proxy.getServer(request.destination().server().value()).orElse(null);
        if (source == null || target == null || !source.getServerInfo().getName().equals(sourceServer)
            || !sourceServer.equals(request.sourceServer()) || source.getServer().equals(target)) {
            reject(request, sourceServer);
            return;
        }
        Pending pending = new Pending(request.requestId(), player, source, target, request.destination(), request.options());
        if (!install(pending)) {
            reject(request, sourceServer);
            return;
        }
        player.createConnectionRequest(target).connect().whenComplete((result, failure) -> {
            if (failure != null || !result.isSuccessful()) {
                abort(pending);
            }
        });
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public EventTask onPreConnect(final ServerPreConnectEvent event) {
        RegisteredServer target = event.getResult().getServer().orElse(null);
        ServerConnection source = event.getPlayer().getCurrentServer().orElse(null);
        if (target == null || event.getPreviousServer() == null) {
            return null;
        }
        if (source == null) {
            // A crashed backend cannot capture native state. A fresh login uses fenced recovery instead.
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            return null;
        }
        if (source.getServer().equals(target)) {
            return null;
        }
        Pending pending = players.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            pending = new Pending(UUID.randomUUID(), event.getPlayer(), source, target, null, TransferOptions.ordinary());
            if (!install(pending)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                return null;
            }
        }
        if (pending.source != source || !pending.target.equals(target)
            || !pending.sent.compareAndSet(false, true)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            return null;
        }
        Pending admitted = pending;
        try {
            DeliveryResult delivery = messages.send(MessageTarget.server(source.getServerInfo().getName()),
                TeleportMessages.PREPARATION, new TeleportPreparation(pending.id, pending.player.getUniqueId(),
                    source.getServerInfo().getName(), target.getServerInfo().getName(), pending.destination, pending.options));
            if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
                abort(pending);
            }
        } catch (RuntimeException failure) {
            abort(pending);
        }
        return EventTask.resumeWhenComplete(pending.approval.thenCompose(allowed -> {
            if (!allowed || admitted.options.admission() == null) {
                return CompletableFuture.completedFuture(allowed);
            }
            return admissions.validate(admitted.options.admission()).exceptionally(failure -> false);
        }).thenAccept(allowed -> {
            if (!allowed || admitted.aborted.get() || !admitted.player.isActive()
                || admitted.player.getCurrentServer().orElse(null) != admitted.source
                || !event.getResult().getServer().map(admitted.target::equals).orElse(false)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                abort(admitted);
            }
        }));
    }

    @Override
    public void prepared(final TeleportPreparationResult result, final String sourceServer) {
        Pending pending = requests.get(result.requestId());
        if (pending != null && pending.source.getServerInfo().getName().equals(sourceServer)) {
            if (result.successful()) {
                pending.approval.complete(true);
            } else {
                abort(pending);
            }
        }
    }

    @Subscribe(async = false)
    public void onPostConnect(final ServerPostConnectEvent event) {
        Pending pending = players.get(event.getPlayer().getUniqueId());
        if (pending != null) {
            if (event.getPlayer().getCurrentServer().map(connection -> connection.getServer().equals(pending.target))
                .orElse(false)) {
                remove(pending);
            } else {
                abort(pending);
            }
        }
    }

    @Subscribe(async = false)
    public void onDisconnect(final DisconnectEvent event) {
        Pending pending = players.get(event.getPlayer().getUniqueId());
        if (pending != null) {
            abort(pending);
        }
    }

    @Override
    public void close() {
        players.values().forEach(this::abort);
        if (started.compareAndSet(true, false)) {
            proxy.getEventManager().unregisterListener(plugin, this);
        }
    }

    private boolean install(final Pending pending) {
        if (!started.get() || players.size() >= 1024 || players.putIfAbsent(pending.player.getUniqueId(), pending) != null) {
            return false;
        }
        requests.put(pending.id, pending);
        CompletableFuture.runAsync(() -> {
            if (requests.get(pending.id) == pending) {
                abort(pending);
            }
        }, CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS));
        return true;
    }

    private void abort(final Pending pending) {
        if (!pending.aborted.compareAndSet(false, true)) {
            return;
        }
        pending.approval.complete(false);
        remove(pending);
        messages.send(MessageTarget.server(pending.source.getServerInfo().getName()), TeleportMessages.ABORT,
            new TeleportAbort(pending.id, pending.player.getUniqueId()));
        if (pending.destination != null) {
            messages.send(MessageTarget.server(pending.source.getServerInfo().getName()), TeleportMessages.COMPLETION,
                new TeleportCompletion(pending.id, TeleportStatus.TRANSFER_REJECTED,
                    "The server connection was rejected or timed out"));
        }
    }

    private void remove(final Pending pending) {
        requests.remove(pending.id, pending);
        players.remove(pending.player.getUniqueId(), pending);
    }

    private void reject(final TeleportTransferRequest request, final String source) {
        if (!source.isBlank()) {
            messages.send(MessageTarget.server(source), TeleportMessages.COMPLETION,
                new TeleportCompletion(request.requestId(), TeleportStatus.TRANSFER_REJECTED,
                    "The source connection changed or another transfer is pending"));
        }
    }

    @RequiredArgsConstructor
    private static final class Pending {

        private final UUID id;
        private final Player player;
        private final ServerConnection source;
        private final RegisteredServer target;
        private final ServerPosition destination;
        private final TransferOptions options;
        private final CompletableFuture<Boolean> approval = new CompletableFuture<>();
        private final AtomicBoolean sent = new AtomicBoolean();
        private final AtomicBoolean aborted = new AtomicBoolean();

    }
}
