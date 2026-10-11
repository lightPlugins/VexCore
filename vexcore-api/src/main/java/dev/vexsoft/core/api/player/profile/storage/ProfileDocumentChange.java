package dev.vexsoft.core.api.player.profile.storage;

import java.util.Objects;

/** An expected-revision write, or a read guard when value is null, in one atomic storage transaction. */
public record ProfileDocumentChange(
    ProfileDocumentKey key, long expectedRevision, String value, long notAfterEpochMillis
) {

    /** Validates and copies this immutable profile value. */
    public ProfileDocumentChange(final ProfileDocumentKey key, final long expectedRevision, final String value) {
        this(key, expectedRevision, value, 0);
    }

    /** Validates and copies this immutable profile value. */
    public ProfileDocumentChange {
        Objects.requireNonNull(key, "key");
        if (expectedRevision < 0 || notAfterEpochMillis < 0 || (value != null && value.length() > 16_777_216)) {
            throw new IllegalArgumentException("Invalid profile document revision or size");
        }
    }

    /** Checks a revision without changing its document, for example a writable-session fence. */
    public static ProfileDocumentChange check(final ProfileDocumentKey key, final long revision) {
        return new ProfileDocumentChange(key, revision, null);
    }

    /** Checks a revision and an expiration against the database clock inside the commit transaction. */
    public static ProfileDocumentChange check(
        final ProfileDocumentKey key, final long revision, final long notAfterEpochMillis
    ) {
        return new ProfileDocumentChange(key, revision, null, notAfterEpochMillis);
    }
}
