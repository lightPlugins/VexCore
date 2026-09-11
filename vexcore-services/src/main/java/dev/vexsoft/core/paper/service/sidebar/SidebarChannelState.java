package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe channel selection for one player's sidebar. */
final class SidebarChannelState {

    private final Map<ChannelKey, Entry> persistent = new HashMap<>();
    private final Map<ChannelKey, Entry> temporary = new HashMap<>();
    private long revision;

    synchronized void setPersistent(
        final ServiceOwner owner,
        final String channel,
        final SidebarFrame frame,
        final int priority,
        final long sequence
    ) {
        persistent.put(key(owner, channel), new Entry(frame, priority, sequence, Long.MAX_VALUE));
        revision++;
    }

    synchronized void showTemporary(
        final ServiceOwner owner,
        final String channel,
        final SidebarFrame frame,
        final int priority,
        final long sequence,
        final long expiresAtNanos
    ) {
        temporary.put(key(owner, channel), new Entry(frame, priority, sequence, expiresAtNanos));
        revision++;
    }

    synchronized boolean clearPersistent(final ServiceOwner owner, final String channel) {
        if (persistent.remove(key(owner, channel)) == null) {
            return false;
        }

        revision++;

        return true;
    }

    synchronized boolean clearTemporary(final ServiceOwner owner, final String channel) {
        if (temporary.remove(key(owner, channel)) == null) {
            return false;
        }

        revision++;

        return true;
    }

    synchronized boolean clear(final ServiceOwner owner) {
        boolean removed = persistent.keySet().removeIf(key -> key.owner() == owner);

        removed |= temporary.keySet().removeIf(key -> key.owner() == owner);

        if (removed) {
            revision++;
        }

        return removed;
    }

    synchronized Selection select(final long nowNanos) {
        if (temporary.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= nowNanos)) {
            revision++;
        }

        Entry selected = null;

        for (Entry entry : persistent.values()) {
            selected = newer(selected, entry);
        }

        for (Entry entry : temporary.values()) {
            selected = newer(selected, entry);
        }

        return new Selection(selected == null ? null : selected.frame(), revision);
    }

    private static Entry newer(final Entry current, final Entry candidate) {
        if (current == null || candidate.priority() > current.priority()
            || candidate.priority() == current.priority() && candidate.sequence() > current.sequence()) {
            return candidate;
        }

        return current;
    }

    private static ChannelKey key(final ServiceOwner owner, final String channel) {
        String checked = Objects.requireNonNull(channel, "channel").trim();

        if (checked.isEmpty() || checked.length() > 128) {
            throw new IllegalArgumentException("channel must contain 1 to 128 characters");
        }

        return new ChannelKey(Objects.requireNonNull(owner, "owner"), checked);
    }

    record Selection(SidebarFrame frame, long revision) {

    }

    private record ChannelKey(ServiceOwner owner, String channel) {

        @Override
        public boolean equals(final Object other) {
            return this == other
                || other instanceof ChannelKey key && owner == key.owner && channel.equals(key.channel);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(owner) + channel.hashCode();
        }
    }

    private record Entry(SidebarFrame frame, int priority, long sequence, long expiresAtNanos) {

        private Entry {
            frame = Objects.requireNonNull(frame, "frame");
        }
    }
}
