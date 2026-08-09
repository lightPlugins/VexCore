package dev.vexsoft.core.stats;

import dev.vexsoft.core.api.player.PlayerContainer;
import java.util.Optional;

/** Provides array-backed stat values for one loaded Vex player. */
public interface StatContainer extends PlayerContainer {

  /** Returns the player view for an active stat registration. */
  PlayerStat getStat(Stat stat);

  /** Finds the player view if this exact stat registration remains active. */
  Optional<PlayerStat> findStat(Stat stat);

  /** Starts a nested update batch that recalculates changed stats when closed. */
  StatUpdateBatch beginUpdate();
}
