package dev.vexsoft.core.api.player.profile.storage;

import java.util.Objects;

/** Detached durable document paired with the revision required for its next update. */
public record StoredProfileDocument(String value, long revision) {

    /** Validates and copies this immutable profile value. */
    public StoredProfileDocument {
        Objects.requireNonNull(value, "value");
        if (revision < 1) {
            throw new IllegalArgumentException("Stored revision must be positive");
        }
    }
}
