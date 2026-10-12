package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Revision-checked handover records shared by all Paper backends. */
public interface PlayerTransferStoreService extends VexService {

    /** Publishes a handover only after its source saves and transactions have drained. */
    CompletableFuture<Void> prepare(PlayerTransferTicket ticket);

    /** Claims a prepared handover for exactly one login on its designated server. */
    CompletableFuture<Optional<PlayerTransferTicket>> claim(UUID playerId, String server, UUID login);

    /** Completes a claimed handover after profile restoration settles. */
    CompletableFuture<Void> finish(PlayerTransferTicket ticket);

    /** Cancels only an unclaimed matching handover; claimed destinations retain ownership. */
    CompletableFuture<Boolean> cancel(UUID playerId, UUID requestId);
}
