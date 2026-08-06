package dev.vexsoft.core.paper.performance;

import dev.vexsoft.core.api.service.VexService;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Displays localized live server performance data in player-specific boss bars */
public interface PerformanceBossBarService extends VexService {

  /** Starts the one-second boss bar updater */
  void start();

  /** Shows the live performance boss bar to a player */
  void show(Player player);

  /** Hides the live performance boss bar from a player */
  void hide(Player player);

  /** Toggles the live performance boss bar and returns its new visibility */
  boolean toggle(Player player);

  /** Checks whether a player currently sees the performance boss bar */
  boolean isVisible(UUID playerId);
}
