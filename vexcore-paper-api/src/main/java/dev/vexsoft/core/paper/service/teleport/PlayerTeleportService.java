package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.teleport.TeleportResult;
import java.util.concurrent.CompletableFuture;

/** Teleports loaded Vex players locally or through Velocity to another backend server. */
public interface PlayerTeleportService extends VexService {

  /**
   * Teleports a player and completes after Paper's asynchronous chunk load and teleport callback.
   */
  CompletableFuture<TeleportResult> teleport(VexPlayer player, ServerPosition destination);
}
