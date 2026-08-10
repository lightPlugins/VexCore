package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Owner-scoped teleport facade backed by the core transfer coordinator. */
@Dependencies(TeleportCoordinatorService.class)
public final class VexPlayerTeleportService implements PlayerTeleportService {

  private final TeleportCoordinatorService coordinator;

  public VexPlayerTeleportService(final VexServiceRegistry services) {
    coordinator = Objects.requireNonNull(services, "services")
        .require(TeleportCoordinatorService.class);
  }

  @Override
  public CompletableFuture<TeleportResult> teleport(
      final VexPlayer player,
      final ServerPosition destination
  ) {
    return coordinator.teleport(player, destination);
  }
}
