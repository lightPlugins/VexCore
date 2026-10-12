package dev.vexsoft.core.paper.service.network;

import dev.vexsoft.core.api.network.admission.BackendPresence;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.world.WorldService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/** Publishes one heartbeat per backend without reading gameplay data or waiting on a server thread. */
@Dependencies({AdmissionService.class, ProfileSessionService.class, ServerIdentityService.class,
    ScheduleService.class, WorldService.class, PlayerDataStoreService.class})
public final class VexBackendPresenceService implements BackendPresenceService, AutoCloseable {

    private final AdmissionService admissions;
    private final ProfileSessionService profiles;
    private final ServerIdentityService identity;
    private final ScheduleService schedules;
    private final WorldService worlds;
    private final PlayerDataStoreService storage;
    private final UUID instance = UUID.randomUUID();
    private final AtomicBoolean writing = new AtomicBoolean();
    private VexTask task;
    private volatile boolean closed;

    public VexBackendPresenceService(final VexServiceRegistry services) {
        admissions = services.require(AdmissionService.class);
        profiles = services.require(ProfileSessionService.class);
        identity = services.require(ServerIdentityService.class);
        schedules = services.require(ScheduleService.class);
        worlds = services.require(WorldService.class);
        storage = services.require(PlayerDataStoreService.class);
    }

    @Override
    public void start() {
        if (task == null) {
            task = schedules.runGlobalTimer(20, 20, this::publish);
        }
    }

    private void publish() {
        if (closed || !writing.compareAndSet(false, true)) {
            return;
        }
        Set<UUID> players = Bukkit.getOnlinePlayers().stream().map(player -> player.getUniqueId())
            .collect(Collectors.toUnmodifiableSet());
        Set<String> loadedWorlds = Bukkit.getWorlds().stream().map(world -> worlds.getKey(world).asString())
            .collect(Collectors.toUnmodifiableSet());
        var presence = new BackendPresence(identity.getServerId(), instance, players, loadedWorlds,
            profiles.domain().map(domain -> domain.value()).orElse(""), profiles.acceptingTransfers(), 0);
        storage.getReadiness().thenCompose(ignored -> admissions.heartbeat(presence))
            .whenComplete((ignored, failure) -> {
                writing.set(false);
                if (closed) {
                    // A publication already in flight must not re-advertise a backend after its shutdown heartbeat.
                    withdraw();
                }
            });
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        withdraw();
    }

    private void withdraw() {
        admissions.heartbeat(new BackendPresence(identity.getServerId(), instance, Set.of(), Set.of(), "", false, 0))
            .exceptionally(failure -> null);
    }
}
