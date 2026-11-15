package dev.vexsoft.core.api.service.network;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Carries login preparation between backends through the shared durable store, even on empty servers. */
public interface JoinPreparationService extends VexService {

    CompletableFuture<Void> enqueue(UUID request, UUID player, String source, String pool, int capacity);

    CompletableFuture<Map<UUID, Request>> status(Collection<UUID> requests);

    CompletableFuture<List<Request>> claim(String pool, int limit);

    CompletableFuture<Void> complete(UUID request, ServerPosition destination);

    CompletableFuture<Void> cancel(UUID request);

    enum Stage { WAITING, PREPARING, READY, UNAVAILABLE }

    record Request(UUID id, UUID player, String source, String pool, long expiresAt,
                   Stage stage, ServerPosition destination) {
    }
}
