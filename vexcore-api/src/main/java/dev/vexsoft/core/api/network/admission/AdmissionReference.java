package dev.vexsoft.core.api.network.admission;

import dev.vexsoft.core.api.network.ServerId;
import java.util.Objects;
import java.util.UUID;

/** Fences one player's admission against cancelled, expired or reassigned reservations. */
public record AdmissionReference(UUID requestId, UUID playerId, String pool, ServerId server, UUID reservationId) {

    /** Validates the immutable reservation identity. */
    public AdmissionReference {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reservationId, "reservationId");
    }
}
