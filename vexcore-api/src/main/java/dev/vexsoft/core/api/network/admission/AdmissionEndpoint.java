package dev.vexsoft.core.api.network.admission;

import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Objects;

/** Configured destination and hard admission limits for one backend. */
public record AdmissionEndpoint(ServerPosition destination, int maximumPlayers, int maximumTransfers) {

    /** Validates the destination and bounded capacity limits. */
    public AdmissionEndpoint {
        Objects.requireNonNull(destination, "destination");
        if (maximumPlayers < 1 || maximumPlayers > 5000 || maximumTransfers < 1 || maximumTransfers > 64) {
            throw new IllegalArgumentException("Invalid admission endpoint capacity");
        }
    }
}
