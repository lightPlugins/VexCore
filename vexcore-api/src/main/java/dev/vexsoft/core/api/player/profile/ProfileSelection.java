package dev.vexsoft.core.api.player.profile;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Captures a game's target membership and revision-bound selection writes for atomic activation. */
public record ProfileSelection(
    ProfileDomain domain,
    UUID profileId,
    long membershipEpoch,
    List<ProfileDocumentChange> changes,
    List<ProfileDocumentChange> writeGuards
) {

    /** Validates and copies this immutable profile value. */
    public ProfileSelection(
        final ProfileDomain domain, final UUID profileId, final long membershipEpoch,
        final List<ProfileDocumentChange> changes
    ) {
        this(domain, profileId, membershipEpoch, changes, List.of());
    }

    /** Validates and copies this immutable profile value. */
    public ProfileSelection {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(profileId, "profileId");
        if (membershipEpoch < 1) {
            throw new IllegalArgumentException("Membership epoch must be positive");
        }
        changes = List.copyOf(changes);
        writeGuards = List.copyOf(writeGuards);
        if (writeGuards.stream().anyMatch(guard -> guard.value() != null)) {
            throw new IllegalArgumentException("Session write guards cannot mutate metadata");
        }
    }
}
