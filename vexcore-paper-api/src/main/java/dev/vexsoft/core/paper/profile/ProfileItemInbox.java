package dev.vexsoft.core.paper.profile;

import java.util.List;

/** Durable serialized inventory overflow owned by a personal profile address. */
public record ProfileItemInbox(List<String> items, List<String> possessionIdentities) {

    /** Validates capacity and copies the durable items and their unique-possession index. */
    public ProfileItemInbox {
        items = List.copyOf(items);
        possessionIdentities = possessionIdentities == null ? List.of() : List.copyOf(possessionIdentities);
        if (items.size() > 4096) {
            throw new IllegalArgumentException("Profile item inbox capacity exceeded");
        }
        if (possessionIdentities.size() > items.size()
            || possessionIdentities.stream().distinct().count() != possessionIdentities.size()) {
            throw new IllegalArgumentException("Duplicated unique inbox possession");
        }
    }

    /** Preserves an inbox without game-specific unique identities. */
    public ProfileItemInbox(final List<String> items) {
        this(items, List.of());
    }

    /** Returns an empty first-activation inbox. */
    public static ProfileItemInbox empty() {
        return new ProfileItemInbox(List.of());
    }
}
