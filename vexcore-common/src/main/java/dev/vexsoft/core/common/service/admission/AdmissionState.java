package dev.vexsoft.core.common.service.admission;

import dev.vexsoft.core.api.network.admission.AdmissionEndpoint;
import dev.vexsoft.core.api.network.admission.AdmissionPool;
import dev.vexsoft.core.api.network.admission.AdmissionStage;
import dev.vexsoft.core.api.network.admission.AdmissionStatus;
import dev.vexsoft.core.api.network.admission.BackendPresence;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Detached admission document; all changes are committed through Core's existing revision fencing. */
record AdmissionState(long nextSequence, Map<String, Policy> pools, Map<UUID, Entry> requests) {

    AdmissionState {
        pools = Map.copyOf(pools);
        requests = Map.copyOf(requests);
    }

    static AdmissionState empty() {
        return new AdmissionState(1, Map.of(), Map.of());
    }

    AdmissionState withEntries(final Map<UUID, Entry> entries) {
        return new AdmissionState(nextSequence, pools, entries);
    }

    AdmissionState prune(final Map<String, BackendPresence> backends, final long now) {
        var result = new LinkedHashMap<UUID, Entry>();
        for (var entry : requests.values()) {
            if (entry.expiresAt() <= now || !pools.containsKey(entry.pool())) {
                continue;
            }
            var target = entry.destination() == null ? null : backends.get(entry.destination().server().value());
            if (entry.stage() == AdmissionStage.ARRIVED && target != null && target.players().contains(entry.player())) {
                continue;
            }
            if (entry.stage() == AdmissionStage.RESERVED && !valid(entry, backends, now)) {
                entry = entry.queued();
            }
            result.put(entry.id(), entry);
        }
        return withEntries(result);
    }

    AdmissionState allocate(final Map<String, BackendPresence> backends, final long now) {
        var result = new LinkedHashMap<>(requests);
        var occupiedByServer = new LinkedHashMap<String, Integer>();
        var transferringByServer = new LinkedHashMap<String, Integer>();
        backends.forEach((server, backend) -> occupiedByServer.put(server, backend.players().size()));
        for (var assigned : requests.values()) {
            if (assigned.destination() == null) {
                continue;
            }
            String server = assigned.destination().server().value();
            var backend = backends.get(server);
            if (backend != null && !backend.players().contains(assigned.player())) {
                occupiedByServer.merge(server, 1, Integer::sum);
            }
            if (assigned.stage() != AdmissionStage.ARRIVED) {
                transferringByServer.merge(server, 1, Integer::sum);
            }
        }
        // Count assignments once; a burst must not rescan the entire queue for every player and candidate server.
        List<Entry> waiting = requests.values().stream().filter(entry -> entry.stage() == AdmissionStage.WAITING)
            .sorted(Comparator.comparingLong(Entry::sequence).thenComparing(Entry::id)).toList();
        for (var entry : waiting) {
            AdmissionPool pool = pools.get(entry.pool()).pool();
            AdmissionEndpoint best = null;
            int bestPlayers = Integer.MAX_VALUE;
            for (var endpoint : pool.endpoints()) {
                String server = endpoint.destination().server().value();
                var presence = backends.get(server);
                if (server.equals(entry.source()) || !available(pool, endpoint, presence, now)) {
                    continue;
                }
                int occupied = occupiedByServer.getOrDefault(server, 0);
                int transferring = transferringByServer.getOrDefault(server, 0);
                if (occupied < endpoint.maximumPlayers() && transferring < endpoint.maximumTransfers()
                    && (best == null || occupied < bestPlayers
                        || occupied == bestPlayers && server.compareTo(best.destination().server().value()) < 0)) {
                    best = endpoint;
                    bestPlayers = occupied;
                }
            }
            if (best != null) {
                var presence = backends.get(best.destination().server().value());
                result.put(entry.id(), entry.assigned(best.destination(), presence.instance()));
                String selected = best.destination().server().value();
                if (!presence.players().contains(entry.player())) {
                    occupiedByServer.merge(selected, 1, Integer::sum);
                }
                transferringByServer.merge(selected, 1, Integer::sum);
            }
        }
        return withEntries(result);
    }

    boolean valid(final Entry entry, final Map<String, BackendPresence> backends, final long now) {
        Policy policy = pools.get(entry.pool());
        if (policy == null || entry.destination() == null || entry.expiresAt() <= now) {
            return false;
        }
        return policy.pool().endpoints().stream().filter(endpoint -> endpoint.destination().equals(entry.destination()))
            .anyMatch(endpoint -> {
                var backend = backends.get(endpoint.destination().server().value());
                return available(policy.pool(), endpoint, backend, now) && backend.instance().equals(entry.instance());
            });
    }

    long validUntil(final Entry entry, final Map<String, BackendPresence> backends) {
        var backend = backends.get(entry.destination().server().value());
        long heartbeatDeadline = backend.observedAt() + pools.get(entry.pool()).pool().heartbeatMaxAgeMillis();
        return Math.min(entry.expiresAt(), heartbeatDeadline);
    }

    AdmissionStatus status(final UUID id) {
        Entry entry = requests.get(id);
        if (entry == null) {
            return new AdmissionStatus(id, AdmissionStage.EXPIRED, 0, null, 0, null);
        }
        int position = entry.stage() == AdmissionStage.WAITING ? 1 + (int) requests.values().stream()
            .filter(other -> other.pool().equals(entry.pool()) && other.stage() == AdmissionStage.WAITING
                && other.sequence() < entry.sequence()).count() : 0;
        return new AdmissionStatus(id, entry.stage(), position, entry.destination(), entry.expiresAt(), entry.reservation());
    }

    private static boolean available(
        final AdmissionPool pool, final AdmissionEndpoint endpoint, final BackendPresence backend, final long now
    ) {
        return backend != null && backend.available() && backend.observedAt() <= now
            && now - backend.observedAt() < pool.heartbeatMaxAgeMillis()
            && backend.profileDomain().equals(pool.profileDomain())
            && backend.worlds().contains(endpoint.destination().world().asString());
    }

    record Policy(long revision, AdmissionPool pool) {
    }

    record Entry(
        UUID id, UUID player, String source, String pool, long sequence, AdmissionStage stage,
        ServerPosition destination, UUID instance, UUID reservation, long expiresAt
    ) {

        Entry queued() {
            return new Entry(id, player, source, pool, sequence, AdmissionStage.WAITING, null, null, null, expiresAt);
        }

        Entry assigned(final ServerPosition target, final UUID backendInstance) {
            return new Entry(id, player, source, pool, sequence, AdmissionStage.RESERVED, target, backendInstance, UUID.randomUUID(), expiresAt);
        }

        Entry renew(final long expiry) {
            return new Entry(id, player, source, pool, sequence, stage, destination, instance, reservation, expiry);
        }

        Entry transferring(final long expiry) {
            return new Entry(id, player, source, pool, sequence, AdmissionStage.TRANSFERRING, destination, instance, reservation, expiry);
        }

        Entry arrived(final long expiry) {
            return new Entry(id, player, source, pool, sequence, AdmissionStage.ARRIVED, destination, instance, reservation, expiry);
        }
    }
}
