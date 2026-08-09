package dev.vexsoft.core.paper.service.players;

import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Default Paper-player resolver backed by the shared loaded-player registry. */
@Dependencies(PlayerService.class)
public final class VexPaperPlayerService implements PaperPlayerService {

  private final PlayerService players;

  public VexPaperPlayerService(final VexServiceRegistry services) {
    players = Objects.requireNonNull(services, "services").require(PlayerService.class);
  }

  @Override
  public Optional<VexPlayer> find(final Player player) {
    return players.find(Objects.requireNonNull(player, "player").getUniqueId());
  }

  @Override
  public VexPlayer require(final Player player) {
    return players.require(Objects.requireNonNull(player, "player").getUniqueId());
  }
}
