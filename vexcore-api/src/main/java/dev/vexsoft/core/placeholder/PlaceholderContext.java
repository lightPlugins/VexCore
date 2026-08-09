package dev.vexsoft.core.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Player-bound input and request-local values used during placeholder resolution. */
public final class PlaceholderContext {

  private final VexPlayer player;
  private final Map<String, String> localValues;

  private PlaceholderContext(
      final VexPlayer player,
      final Map<String, String> localValues
  ) {
    this.player = player;
    this.localValues = localValues;
  }

  /** Creates a context without local placeholders. */
  public static PlaceholderContext of(final VexPlayer player) {
    return new PlaceholderContext(Objects.requireNonNull(player, "player"), Map.of());
  }

  /** Returns a copy containing one temporary placeholder. */
  public PlaceholderContext with(final String name, final Object value) {
    String key = normalizeLocalName(name);
    Map<String, String> updated = new LinkedHashMap<>(localValues);
    updated.put(key, Objects.toString(value, ""));
    return new PlaceholderContext(player, Map.copyOf(updated));
  }

  /** Returns the player required for every resolution. */
  public VexPlayer getPlayer() {
    return player;
  }

  /** Returns one temporary placeholder value, or {@code null} when it is not present. */
  public String getLocalValue(final String name) {
    return localValues.get(Objects.requireNonNull(name, "name"));
  }

  private static String normalizeLocalName(final String name) {
    String checked = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
    if (!checked.matches("[a-z0-9]+(?:_[a-z0-9]+)*")) {
      throw new IllegalArgumentException("Invalid local placeholder name: " + name);
    }
    return checked;
  }
}
