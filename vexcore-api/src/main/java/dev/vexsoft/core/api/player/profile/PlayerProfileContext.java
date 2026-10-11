package dev.vexsoft.core.api.player.profile;

import java.util.Objects;
import java.util.UUID;

/** Captured session identity used to reject old callbacks after switching, reconnecting or readmission. */
public record PlayerProfileContext(
    ProfileDomain domain,
    UUID profileId,
    UUID playerId,
    UUID sessionId,
    long generation,
    long membershipEpoch,
    long fence
) {

    /** Validates and copies this immutable profile value. */
    public PlayerProfileContext {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (generation < 0 || membershipEpoch < 1 || fence < 1) {
            throw new IllegalArgumentException("Invalid profile context counters");
        }
    }

    /** Returns the explicit personal persistence destination captured by this context. */
    public PlayerDataAddress address() {
        return PlayerDataAddress.personal(domain, profileId, playerId);
    }
}
