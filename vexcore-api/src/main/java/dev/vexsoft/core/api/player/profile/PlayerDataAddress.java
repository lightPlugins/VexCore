package dev.vexsoft.core.api.player.profile;

import java.util.Objects;
import java.util.UUID;

/** Immutable persistence identity; a profile UUID never replaces a player's network identity. */
public record PlayerDataAddress(ProfileDomain domain, UUID profileId, UUID playerId) {

    /** Validates and copies this immutable profile value. */
    public PlayerDataAddress {
        if (domain == null) {
            if (profileId != null || playerId == null) {
                throw new IllegalArgumentException("Account data requires a player and no profile");
            }
        } else {
            Objects.requireNonNull(profileId, "profileId");
        }
    }

    /** Addresses account data independently of any active profile. */
    public static PlayerDataAddress account(final UUID playerId) {
        return new PlayerDataAddress(null, null, Objects.requireNonNull(playerId, "playerId"));
    }

    /** Addresses one member's personal save inside a shared profile. */
    public static PlayerDataAddress personal(final ProfileDomain domain, final UUID profileId, final UUID playerId) {
        return new PlayerDataAddress(Objects.requireNonNull(domain, "domain"), profileId,
            Objects.requireNonNull(playerId, "playerId"));
    }

    /** Addresses authoritative shared data without pretending it belongs to an individual member. */
    public static PlayerDataAddress shared(final ProfileDomain domain, final UUID profileId) {
        return new PlayerDataAddress(Objects.requireNonNull(domain, "domain"), profileId, null);
    }

    /** Returns a stable unambiguous storage identifier for indexed profile documents. */
    public String storageKey() {
        return domain == null ? "account/" + playerId
            : "profile/" + domain.value() + "/" + profileId + "/" + (playerId == null ? "shared" : playerId);
    }
}
