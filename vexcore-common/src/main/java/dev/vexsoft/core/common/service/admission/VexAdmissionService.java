package dev.vexsoft.core.common.service.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.network.admission.AdmissionPool;
import dev.vexsoft.core.api.network.admission.AdmissionReference;
import dev.vexsoft.core.api.network.admission.AdmissionStage;
import dev.vexsoft.core.api.network.admission.AdmissionStatus;
import dev.vexsoft.core.api.network.admission.BackendPresence;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Uses the existing authoritative Core store for cross-proxy FIFO admission and fenced reservations. */
@Dependencies(ProfileStorageService.class)
public final class VexAdmissionService implements AdmissionService {

    private static final ProfileDomain DOMAIN = new ProfileDomain("vexcore:admission");
    private static final ProfileDocumentKey STATE = key("queues");
    private final ProfileStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public VexAdmissionService(final VexServiceRegistry services) {
        this(services.require(ProfileStorageService.class));
    }

    VexAdmissionService(final ProfileStorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletableFuture<Void> configure(final AdmissionPool pool, final long revision) {
        if (revision < 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Routing policy needs a published revision"));
        }
        return update(false, (snapshot, now) -> {
            var policies = new LinkedHashMap<>(snapshot.state().pools());
            var previous = policies.get(pool.id());
            if (previous == null || previous.revision() < revision) {
                if (policies.size() >= 64 && previous == null) {
                    throw new IllegalStateException("Too many admission pools");
                }
                policies.put(pool.id(), new AdmissionState.Policy(revision, pool));
            } else if (previous.revision() == revision && !previous.pool().equals(pool)) {
                throw new IllegalStateException("Conflicting routing policy revision");
            }
            return new Change<Void>(new AdmissionState(snapshot.state().nextSequence(), policies,
                snapshot.state().requests()), null, 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Void> heartbeat(final BackendPresence presence) {
        return heartbeat(presence, 0);
    }

    @Override
    public CompletableFuture<AdmissionStatus> enqueue(
        final UUID requestId, final UUID playerId, final String sourceServer, final String pool
    ) {
        new ServerId(sourceServer);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        return update(true, (snapshot, now) -> {
            var state = snapshot.state().prune(snapshot.backends(), now);
            var policy = state.pools().get(pool);
            if (policy == null) {
                throw new IllegalStateException("Admission pool is unavailable");
            }
            var previous = state.requests().get(requestId);
            if (previous != null) {
                if (!previous.player().equals(playerId) || !previous.source().equals(sourceServer)
                    || !previous.pool().equals(pool)) {
                    throw new IllegalStateException("Admission request identity mismatch");
                }
                return new Change<>(state, state.status(requestId), 0);
            }
            if (state.requests().values().stream().anyMatch(entry -> entry.player().equals(playerId))) {
                throw new IllegalStateException("Player already has an admission request");
            }
            long waiting = state.requests().values().stream().filter(entry -> entry.pool().equals(pool)).count();
            if (state.requests().size() >= 4096 || waiting >= policy.pool().maximumQueued()) {
                throw new IllegalStateException("Admission queue is full");
            }
            var entries = new LinkedHashMap<>(state.requests());
            entries.put(requestId, new AdmissionState.Entry(requestId, playerId, sourceServer, pool,
                state.nextSequence(), AdmissionStage.WAITING, null, null, null, now + policy.pool().leaseMillis()));
            var changed = new AdmissionState(Math.addExact(state.nextSequence(), 1), state.pools(), entries)
                .allocate(snapshot.backends(), now);
            return new Change<>(changed, changed.status(requestId), 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Map<UUID, AdmissionStatus>> pulse(final Collection<UUID> requestIds) {
        var identifiers = List.copyOf(requestIds);
        return update(true, (snapshot, now) -> {
            var state = snapshot.state().prune(snapshot.backends(), now);
            var entries = new LinkedHashMap<>(state.requests());
            for (var id : identifiers) {
                var entry = entries.get(id);
                if (entry != null && (entry.stage() == AdmissionStage.WAITING || entry.stage() == AdmissionStage.RESERVED)) {
                    entries.put(id, entry.renew(now + state.pools().get(entry.pool()).pool().leaseMillis()));
                }
            }
            var changed = state.withEntries(entries).allocate(snapshot.backends(), now);
            var statuses = new LinkedHashMap<UUID, AdmissionStatus>();
            identifiers.forEach(id -> statuses.put(id, changed.status(id)));
            return new Change<>(changed, Map.copyOf(statuses), 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Boolean> cancel(final UUID requestId, final UUID playerId) {
        return update(false, (snapshot, now) -> {
            var entry = snapshot.state().requests().get(requestId);
            boolean cancellable = entry != null && entry.player().equals(playerId)
                && (entry.stage() == AdmissionStage.WAITING || entry.stage() == AdmissionStage.RESERVED);
            var entries = new LinkedHashMap<>(snapshot.state().requests());
            if (cancellable) {
                entries.remove(requestId);
            }
            return new Change<>(snapshot.state().withEntries(entries), cancellable, 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Boolean> begin(final AdmissionReference reference) {
        return update(true, (snapshot, now) -> {
            var state = snapshot.state();
            var entry = state.requests().get(reference.requestId());
            boolean valid = matches(entry, reference) && entry.stage() == AdmissionStage.RESERVED
                && state.valid(entry, snapshot.backends(), now);
            var entries = new LinkedHashMap<>(state.requests());
            if (valid) {
                entries.put(entry.id(), entry.transferring(now + 120000));
            }
            return new Change<>(state.withEntries(entries), valid, valid ? state.validUntil(entry, snapshot.backends()) : 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Boolean> validate(final AdmissionReference reference) {
        return update(true, (snapshot, now) -> {
            var entry = snapshot.state().requests().get(reference.requestId());
            boolean valid = matches(entry, reference) && entry.stage() == AdmissionStage.TRANSFERRING
                && snapshot.state().valid(entry, snapshot.backends(), now);
            return new Change<>(snapshot.state(), valid, valid ? entry.expiresAt() : 0);
        }, 0);
    }

    @Override
    public CompletableFuture<Void> finish(final AdmissionReference reference, final boolean success) {
        return update(false, (snapshot, now) -> {
            var entries = new LinkedHashMap<>(snapshot.state().requests());
            var entry = entries.get(reference.requestId());
            if (matches(entry, reference)) {
                if (success && entry.stage() == AdmissionStage.TRANSFERRING) {
                    // Keep capacity reserved until a target heartbeat includes the player; do not double-count them.
                    entries.put(entry.id(), entry.arrived(now + 30000));
                } else if (!success && entry.stage() == AdmissionStage.TRANSFERRING) {
                    entries.remove(entry.id());
                }
            }
            return new Change<Void>(snapshot.state().withEntries(entries), null, 0);
        }, 0);
    }

    private CompletableFuture<Void> heartbeat(final BackendPresence presence, final int attempt) {
        var documentKey = key("backend:" + presence.server().value());
        return storage.databaseTime().thenComposeAsync(now -> storage.load(List.of(documentKey)).thenCompose(documents -> {
            var document = documents.get(documentKey);
            if (document != null) {
                var previous = decode(document.value(), BackendPresence.class);
                if (previous.available() && !previous.instance().equals(presence.instance())
                    && now - previous.observedAt() < 10000) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Duplicate live backend identity"));
                }
            }
            var current = new BackendPresence(presence.server(), presence.instance(), presence.players(),
                presence.worlds(), presence.profileDomain(), presence.available(), now);
            return storage.commit(List.of(new ProfileDocumentChange(documentKey, revision(document), encode(current))))
                .thenCompose(committed -> committed ? CompletableFuture.completedFuture(null)
                    : attempt < 16 ? heartbeat(presence, attempt + 1)
                    : CompletableFuture.failedFuture(new IllegalStateException("Backend heartbeat revision conflict")));
        }));
    }

    private <T> CompletableFuture<T> update(
        final boolean includeBackends, final BiFunction<Snapshot, Long, Change<T>> operation, final int attempt
    ) {
        return storage.databaseTime().thenComposeAsync(now -> snapshot(includeBackends).thenCompose(snapshot -> {
            Change<T> changed = operation.apply(snapshot, now);
            if (changed.state().equals(snapshot.state()) && changed.notAfter() == 0) {
                return CompletableFuture.completedFuture(changed.result());
            }
            var guards = new ArrayList<ProfileDocumentChange>();
            guards.add(new ProfileDocumentChange(STATE, revision(snapshot.document()),
                changed.state().equals(snapshot.state()) ? null : encode(changed.state()), changed.notAfter()));
            snapshot.guards().forEach((key, revision) -> guards.add(ProfileDocumentChange.check(key, revision)));
            return storage.commit(guards).thenCompose(committed -> {
                if (committed) {
                    return CompletableFuture.completedFuture(changed.result());
                }
                if (attempt >= 128) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Admission state changed too frequently"));
                }
                return update(includeBackends, operation, attempt + 1);
            });
        }));
    }

    private CompletableFuture<Snapshot> snapshot(final boolean includeBackends) {
        return storage.load(List.of(STATE)).thenCompose(documents -> {
            var document = documents.get(STATE);
            var state = document == null ? AdmissionState.empty() : decode(document.value(), AdmissionState.class);
            if (!includeBackends || state.pools().isEmpty()) {
                return CompletableFuture.completedFuture(new Snapshot(document, state, Map.of(), Map.of()));
            }
            var servers = state.pools().values().stream().flatMap(policy -> policy.pool().endpoints().stream())
                .map(endpoint -> endpoint.destination().server().value()).distinct().toList();
            var keys = servers.stream().map(server -> key("backend:" + server)).toList();
            return storage.load(keys).thenApply(presence -> {
                var backends = new HashMap<String, BackendPresence>();
                for (String server : servers) {
                    var stored = presence.get(key("backend:" + server));
                    if (stored != null) {
                        backends.put(server, decode(stored.value(), BackendPresence.class));
                    }
                }
                var guards = new LinkedHashMap<ProfileDocumentKey, Long>();
                keys.forEach(key -> guards.put(key, revision(presence.get(key))));
                return new Snapshot(document, state, Map.copyOf(backends), Map.copyOf(guards));
            });
        });
    }

    private static boolean matches(final AdmissionState.Entry entry, final AdmissionReference reference) {
        return entry != null && entry.player().equals(reference.playerId()) && entry.pool().equals(reference.pool())
            && entry.destination() != null && entry.destination().server().equals(reference.server())
            && reference.reservationId().equals(entry.reservation());
    }

    private static ProfileDocumentKey key(final String identity) {
        var id = UUID.nameUUIDFromBytes(("vexcore:admission:" + identity).getBytes(StandardCharsets.UTF_8));
        return new ProfileDocumentKey(PlayerDataAddress.shared(DOMAIN, id), "vexcore", "admission");
    }

    private static long revision(final StoredProfileDocument document) {
        return document == null ? 0 : document.revision();
    }

    private String encode(final Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to serialize admission state", failure);
        }
    }

    private <T> T decode(final String value, final Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid stored admission state", failure);
        }
    }

    private record Snapshot(
        StoredProfileDocument document, AdmissionState state, Map<String, BackendPresence> backends,
        Map<ProfileDocumentKey, Long> guards
    ) {
    }

    private record Change<T>(AdmissionState state, T result, long notAfter) {
    }
}
