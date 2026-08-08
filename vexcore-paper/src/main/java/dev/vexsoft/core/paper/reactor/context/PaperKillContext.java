package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import lombok.Value;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Value
public class PaperKillContext implements KillReactorContext {
  VexPlayer player;
  LivingEntity target;

  /** Creates a context for a living entity killed by a player. */
  public PaperKillContext(final VexPlayer player, final LivingEntity target) {
    this.player = Objects.requireNonNull(player, "player");
    this.target = Objects.requireNonNull(target, "target");
  }

  @Override
  public Object getVariable(final String name) {
    return switch (name) {
      case "player-health" -> player.requirePlatformPlayer(Player.class).getHealth();
      case "victim-health" -> target.getHealth();
      default -> null;
    };
  }
}
