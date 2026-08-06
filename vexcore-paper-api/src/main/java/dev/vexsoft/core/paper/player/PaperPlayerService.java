package dev.vexsoft.core.paper.player;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Resolves fully loaded Vex players from native Paper player objects. */
public interface PaperPlayerService extends VexService {

  /** Finds the loaded Vex player represented by a native Paper player. */
  Optional<VexPlayer> find(Player player);

  /** Returns the loaded Vex player represented by a native Paper player. */
  VexPlayer require(Player player);
}
