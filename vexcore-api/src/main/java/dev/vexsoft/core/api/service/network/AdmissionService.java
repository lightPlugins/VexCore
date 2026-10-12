package dev.vexsoft.core.api.service.network;

import dev.vexsoft.core.api.network.admission.AdmissionPool;
import dev.vexsoft.core.api.network.admission.AdmissionReference;
import dev.vexsoft.core.api.network.admission.AdmissionStatus;
import dev.vexsoft.core.api.network.admission.BackendPresence;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owns shared FIFO queues, atomic capacity reservations and fenced transfer admission. */
public interface AdmissionService extends VexService {

    /** Installs a routing policy only when its published database revision is newer. */
    CompletableFuture<Void> configure(AdmissionPool pool, long revision);

    /** Publishes a backend heartbeat using authoritative database time. */
    CompletableFuture<Void> heartbeat(BackendPresence presence);

    /** Enqueues one idempotent request; a player may have only one request across all pools. */
    CompletableFuture<AdmissionStatus> enqueue(UUID requestId, UUID playerId, String sourceServer, String pool);

    /** Renews the caller's live requests and allocates queued entries atomically across the network. */
    CompletableFuture<Map<UUID, AdmissionStatus>> pulse(Collection<UUID> requestIds);

    /** Cancels a matching waiting or reserved request; transfers cannot be cancelled through this method. */
    CompletableFuture<Boolean> cancel(UUID requestId, UUID playerId);

    /** Consumes a live reservation into the bounded transfer phase. */
    CompletableFuture<Boolean> begin(AdmissionReference reference);

    /** Verifies an in-flight reservation before source preparation, connection or destination restoration. */
    CompletableFuture<Boolean> validate(AdmissionReference reference);

    /** Settles an admitted transfer and preserves occupancy until a heartbeat observes successful arrival. */
    CompletableFuture<Void> finish(AdmissionReference reference, boolean success);
}
