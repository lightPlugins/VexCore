package dev.vexsoft.core.api.network.admission;

import dev.vexsoft.core.api.world.ServerPosition;
import java.util.UUID;

/** Detached entry status for rendering; queue positions are one-based and zero outside the queue. */
public record AdmissionStatus(
    UUID requestId, AdmissionStage stage, int position, ServerPosition destination, long leaseEndsAt, UUID reservationId
) {
}
