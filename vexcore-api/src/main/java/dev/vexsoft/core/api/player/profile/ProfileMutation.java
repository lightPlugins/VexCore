package dev.vexsoft.core.api.player.profile;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import java.util.List;

/** A prepared economic result with optional metadata writes committed together with personal profile data. */
public record ProfileMutation<T>(T result, List<ProfileDocumentChange> changes) {

    /** Validates and copies this immutable profile value. */
    public ProfileMutation {
        changes = List.copyOf(changes);
    }

    /** Creates a mutation whose entire durable state is held in registered profile containers. */
    public static <T> ProfileMutation<T> of(final T result) {
        return new ProfileMutation<>(result, List.of());
    }
}
