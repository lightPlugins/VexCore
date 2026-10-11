package dev.vexsoft.core.api.player.profile;

import java.util.Objects;

/** Identifies an explicitly registered profile domain shared by participating plugins. */
public record ProfileDomain(String value) {

    /** Validates and copies this immutable profile value. */
    public ProfileDomain {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9._-]+:[a-z0-9._/-]+") || value.length() > 96) {
            throw new IllegalArgumentException("Invalid profile domain: " + value);
        }
    }
}
