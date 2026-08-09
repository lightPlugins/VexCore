package dev.vexsoft.core.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;

/** Resolves one owner-namespaced placeholder for a loaded Vex player. */
@FunctionalInterface
public interface VexPlaceholder {

  /**
   * Resolves the supplied argument path.
   *
   * @return replacement text, or {@code null} when this argument path is unsupported
   */
  String resolve(VexPlayer player, PlaceholderArguments arguments);
}
