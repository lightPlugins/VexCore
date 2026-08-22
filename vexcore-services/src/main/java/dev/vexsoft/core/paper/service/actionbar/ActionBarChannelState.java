package dev.vexsoft.core.paper.service.actionbar;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/** Thread-safe selection state for one player's action-bar channels. */
final class ActionBarChannelState {

  private final Map<ChannelKey, Entry> persistent = new HashMap<>();
  private final Map<ChannelKey, Entry> temporary = new HashMap<>();
  private long revision;

  synchronized void setPersistent(
      final ServiceOwner owner,
      final String channel,
      final Component component,
      final int priority,
      final long sequence
  ) {
    persistent.put(key(owner, channel), new Entry(component, priority, sequence, Long.MAX_VALUE));
    revision++;
  }

  synchronized void showTemporary(
      final ServiceOwner owner,
      final String channel,
      final Component component,
      final int priority,
      final long sequence,
      final long expiresAtNanos
  ) {
    temporary.put(
        key(owner, channel),
        new Entry(component, priority, sequence, expiresAtNanos)
    );
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
    Map<ChannelKey, Entry> layer = temporary.isEmpty() ? persistent : temporary;
    Entry selected = null;
    for (Entry entry : layer.values()) {
      if (selected == null || entry.priority() > selected.priority()
          || entry.priority() == selected.priority() && entry.sequence() > selected.sequence()) {
        selected = entry;
      }
    }
    return new Selection(selected == null ? null : selected.component(), revision);
  }

  private static ChannelKey key(final ServiceOwner owner, final String channel) {
    return new ChannelKey(
        Objects.requireNonNull(owner, "owner"),
        requireChannel(channel)
    );
  }

  private static String requireChannel(final String channel) {
    String checked = Objects.requireNonNull(channel, "channel").trim();
    if (checked.isEmpty()) {
      throw new IllegalArgumentException("channel must not be blank");
    }
    if (checked.length() > 128) {
      throw new IllegalArgumentException("channel must not exceed 128 characters");
    }
    return checked;
  }

  /** Currently selected component and state revision. */
  record Selection(Component component, long revision) {

    Optional<Component> selected() {
      return Optional.ofNullable(component);
    }
  }

  private record ChannelKey(ServiceOwner owner, String channel) {

    @Override
    public boolean equals(final Object other) {
      return this == other || other instanceof ChannelKey key
          && owner == key.owner && channel.equals(key.channel);
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(owner) + channel.hashCode();
    }
  }

  private record Entry(
      Component component,
      int priority,
      long sequence,
      long expiresAtNanos
  ) {

    private Entry {
      component = Objects.requireNonNull(component, "component");
    }
  }
}
