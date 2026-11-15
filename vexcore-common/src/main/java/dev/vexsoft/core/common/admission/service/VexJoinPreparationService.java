package dev.vexsoft.core.common.admission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.network.JoinPreparationService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Owns one revision-checked, bounded queue independent of platform message carriers. */
@Dependencies(ProfileStorageService.class)
public final class VexJoinPreparationService implements JoinPreparationService {

    private static final ProfileDocumentKey KEY = new ProfileDocumentKey(
        PlayerDataAddress.account(UUID.fromString("26e6feeb-427f-4278-8e4f-71b68396f4c2")), "vexcore", "join_preparation");
    private static final long LEASE_MILLIS = 60_000;
    private static final int RETRIES = 32;
    private final ProfileStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public VexJoinPreparationService(final VexServiceRegistry services) {
        this(services.require(ProfileStorageService.class));
    }

    VexJoinPreparationService(final ProfileStorageService storage) {
        this.storage = storage;
    }

    @Override
    public CompletableFuture<Void> enqueue(final UUID id, final UUID player, final String source,
                                           final String pool, final int capacity) {
        return change(snapshot -> {
            if (snapshot.requests().values().stream().filter(request -> request.pool().equals(pool)).count() >= capacity) {
                throw new IllegalStateException("The join preparation queue is full");
            }
            snapshot.requests().put(id, new Request(id, player, source, pool, snapshot.now() + LEASE_MILLIS,
                Stage.WAITING, null));
            return null;
        }, RETRIES);
    }

    @Override
    public CompletableFuture<Map<UUID, Request>> status(final Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return snapshot().thenApply(snapshot -> {
            var found = new LinkedHashMap<UUID, Request>();
            ids.forEach(id -> {
                Request request = snapshot.requests().get(id);
                if (request != null) {
                    found.put(id, request);
                }
            });
            return Map.copyOf(found);
        });
    }

    @Override
    public CompletableFuture<List<Request>> claim(final String pool, final int limit) {
        if (limit < 1) {
            return CompletableFuture.completedFuture(List.of());
        }
        return change(snapshot -> {
            var claimed = new ArrayList<Request>();
            for (var entry : snapshot.requests().entrySet()) {
                Request request = entry.getValue();
                if (request.pool().equals(pool) && request.stage() == Stage.WAITING) {
                    var preparation = update(request, Stage.PREPARING, null);
                    entry.setValue(preparation);
                    claimed.add(preparation);
                    if (claimed.size() == limit) {
                        break;
                    }
                }
            }
            return List.copyOf(claimed);
        }, RETRIES);
    }

    @Override
    public CompletableFuture<Void> complete(final UUID id, final ServerPosition destination) {
        return change(snapshot -> {
            Request request = snapshot.requests().get(id);
            if (request != null && request.stage() == Stage.PREPARING) {
                snapshot.requests().put(id, update(request,
                    destination == null ? Stage.UNAVAILABLE : Stage.READY, destination));
            }
            return null;
        }, RETRIES);
    }

    @Override
    public CompletableFuture<Void> cancel(final UUID id) {
        return change(snapshot -> {
            snapshot.requests().remove(id);
            return null;
        }, RETRIES);
    }

    private <T> CompletableFuture<T> change(final Function<Snapshot, T> mutation, final int attempts) {
        return snapshot().thenCompose(snapshot -> {
            var before = Map.copyOf(snapshot.requests());
            T result = mutation.apply(snapshot);
            if (!snapshot.pruned() && before.equals(snapshot.requests())) {
                return CompletableFuture.completedFuture(result);
            }
            return storage.commit(List.of(new ProfileDocumentChange(KEY, snapshot.revision(), encode(snapshot.requests()))))
                .thenCompose(committed -> {
                    if (committed) {
                        return CompletableFuture.completedFuture(result);
                    }
                    if (attempts <= 1) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Join preparation is busy"));
                    }
                    return change(mutation, attempts - 1);
                });
        });
    }

    private CompletableFuture<Snapshot> snapshot() {
        return storage.load(List.of(KEY)).thenCombine(storage.databaseTime(), (documents, now) -> {
            var document = documents.get(KEY);
            var requests = document == null ? new LinkedHashMap<UUID, Request>() : decode(document.value());
            boolean pruned = requests.values().removeIf(request -> request.expiresAt() <= now);
            return new Snapshot(requests, document == null ? 0 : document.revision(), now, pruned);
        });
    }

    private String encode(final Map<UUID, Request> requests) {
        try {
            return mapper.writeValueAsString(requests);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode join preparation", failure);
        }
    }

    private LinkedHashMap<UUID, Request> decode(final String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot decode join preparation", failure);
        }
    }

    private static Request update(final Request request, final Stage stage, final ServerPosition destination) {
        return new Request(request.id(), request.player(), request.source(), request.pool(), request.expiresAt(),
            stage, destination);
    }

    private record Snapshot(LinkedHashMap<UUID, Request> requests, long revision, long now, boolean pruned) {
    }
}
