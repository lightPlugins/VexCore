package dev.vexsoft.core.api.player.profile.storage;

import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import java.util.Objects;

/** Identifies one owner-scoped document at an explicit account, personal or shared address. */
public record ProfileDocumentKey(PlayerDataAddress address, String owner, String name)
    implements Comparable<ProfileDocumentKey> {

    /** Validates and copies this immutable profile value. */
    public ProfileDocumentKey {
        Objects.requireNonNull(address, "address");
        if (owner == null || !owner.matches("[a-z][a-z0-9_]{0,50}")) {
            throw new IllegalArgumentException("Invalid document owner");
        }
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid document name");
        }
    }

    /** Defines a stable row-lock order across accounts and profiles. */
    @Override
    public int compareTo(final ProfileDocumentKey other) {
        int result = address.storageKey().compareTo(other.address.storageKey());
        if (result == 0) {
            result = owner.compareTo(other.owner);
        }
        return result == 0 ? name.compareTo(other.name) : result;
    }
}
