package dev.vexsoft.core.velocity.service.directory;

import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.network.NetworkPresenceService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Publishes the proxy's authoritative connections even while every Garden backend is empty. */
@Dependencies(NetworkPresenceService.class)
public final class VexNetworkPresencePublisherService implements NetworkPresencePublisherService, AutoCloseable {

    private final VexCoreVelocityPlugin plugin;
    private final NetworkPresenceService presence;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private volatile boolean running;
    private volatile CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private ScheduledTask task;

    public VexNetworkPresencePublisherService(final VexServiceRegistry services) {
        if (!(services.getOwner() instanceof VexCoreVelocityPlugin owner)) {
            throw new IllegalArgumentException("Network presence publishing requires the Core proxy owner");
        }
        plugin = owner;
        presence = services.require(NetworkPresenceService.class);
    }

    @Override
    public void start() {
        if (!running) {
            running = true;
            task = plugin.getProxyServer().getScheduler().buildTask(plugin, this::publish)
                .repeat(2, TimeUnit.SECONDS).schedule();
        }
    }

    @Override
    public void close() {
        CompletableFuture<Void> drain;
        synchronized (this) {
            running = false;
            if (task != null) {
                task.cancel();
            }
            drain = pending;
        }
        drain.handle((ignored, failure) -> null).thenCompose(ignored -> presence.withdraw()).join();
    }

    private synchronized void publish() {
        if (!running || !publishing.compareAndSet(false, true)) {
            return;
        }
        var online = plugin.getProxyServer().getAllPlayers().stream().flatMap(player -> player.getCurrentServer().stream()
            .map(server -> new NetworkPlayer(player.getUniqueId(), player.getUsername(),
                new ServerId(server.getServerInfo().getName())))).toList();
        pending = presence.publish(online).whenComplete((ignored, failure) -> {
            publishing.set(false);
            if (failure != null && running) {
                plugin.getPlatformLogger().warn("Cannot publish authoritative proxy presence", failure);
            }
        });
    }
}
