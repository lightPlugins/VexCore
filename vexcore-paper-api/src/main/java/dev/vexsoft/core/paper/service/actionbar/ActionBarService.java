package dev.vexsoft.core.paper.service.actionbar;

import dev.vexsoft.core.api.service.registry.VexService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Manages owner-scoped persistent and temporary player action-bar channels.
 *
 * <p>Temporary channels take precedence over persistent channels. Within the same layer, the
 * highest priority wins and the most recently updated channel breaks ties. When a temporary line
 * expires, the currently selected persistent line becomes visible again automatically.</p>
 */
public interface ActionBarService extends VexService {

  /** Sets a persistent line with the default priority of zero. */
  default void setPersistent(
      final Player player,
      final String channel,
      final Component component
  ) {
    setPersistent(player, channel, component, 0);
  }

  /** Sets or replaces one persistent owner channel. */
  void setPersistent(Player player, String channel, Component component, int priority);

  /** Shows a temporary line with the default priority of zero. */
  default void showTemporary(
      final Player player,
      final String channel,
      final Component component,
      final long durationTicks
  ) {
    showTemporary(player, channel, component, durationTicks, 0);
  }

  /** Sets or replaces one temporary owner channel for the given positive duration. */
  void showTemporary(
      Player player,
      String channel,
      Component component,
      long durationTicks,
      int priority
  );

  /** Removes one persistent channel owned by this service scope. */
  void clearPersistent(Player player, String channel);

  /** Removes one temporary channel owned by this service scope. */
  void clearTemporary(Player player, String channel);

  /** Removes every action-bar channel owned by this service scope for the player. */
  void clear(Player player);
}
