package dev.vexsoft.core.api.player.profile;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import java.util.Map;
import java.util.Objects;

/** Detached typed values and durable revisions for one personal-profile session. */
public record ProfileDataSnapshot(
    PlayerProfileContext context,
    Map<DataContainerKey<?>, Object> values,
    Map<ProfileDocumentKey, Long> revisions
) {

    /** Validates and copies this immutable profile value. */
    public ProfileDataSnapshot {
        Objects.requireNonNull(context, "context");
        values = Map.copyOf(values);
        revisions = Map.copyOf(revisions);
    }
}
