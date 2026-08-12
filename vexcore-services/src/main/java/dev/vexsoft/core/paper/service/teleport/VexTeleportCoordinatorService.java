package dev.vexsoft.core.paper.service.teleport;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.world.WorldService;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Default async Paper teleport and Velocity transfer coordinator. */
@Dependencies({
    WorldService.class,
    ServerIdentityService.class,
    MessagingService.class,
    PlayerDataCoordinatorService.class
})
public final class VexTeleportCoordinatorService implements
    TeleportCoordinatorService,
    AutoCloseable {

  private static final long TRANSFER_TIMEOUT_SECONDS = 30L;

  private final JavaPlugin plugin;
  private final WorldService worlds;
  private final ServerIdentityService serverIdentity;
  private final MessagingService messages;
  private final PlayerDataCoordinatorService playerData;
  private final ConcurrentHashMap<UUID, CompletableFuture<TeleportResult>> pending =
      new ConcurrentHashMap<>();

  public VexTeleportCoordinatorService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof JavaPlugin javaPlugin)) {
      throw new IllegalArgumentException("Teleport coordinator owner must be a JavaPlugin");
    }
    plugin = javaPlugin;
    worlds = checkedServices.require(WorldService.class);
    serverIdentity = checkedServices.require(ServerIdentityService.class);
    messages = checkedServices.require(MessagingService.class);
    playerData = checkedServices.require(PlayerDataCoordinatorService.class);
  }

  @Override
  public CompletableFuture<TeleportResult> teleport(
      final VexPlayer player,
      final ServerPosition destination
  ) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    ServerPosition checkedDestination = Objects.requireNonNull(destination, "destination");
    if (checkedDestination.server().equals(serverIdentity.getServerId())) {
      return checkedPlayer.findPlatformPlayer(Player.class)
          .map(platformPlayer -> teleportLocal(platformPlayer, checkedDestination))
          .orElseGet(() -> completed(
              TeleportStatus.PLAYER_OFFLINE,
              "The player is no longer online"
          ));
    }
    return transfer(checkedPlayer, checkedDestination);
  }

  @Override
  public CompletableFuture<TeleportResult> acceptArrival(
      final UUID playerId,
      final ServerPosition destination
  ) {
    Player player = plugin.getServer().getPlayer(Objects.requireNonNull(playerId, "playerId"));
    if (player == null) {
      return completed(TeleportStatus.PLAYER_OFFLINE, "The player did not reach this server");
    }
    if (!destination.server().equals(serverIdentity.getServerId())) {
      return completed(
          TeleportStatus.SERVER_UNAVAILABLE,
          "The teleport arrived on the wrong server"
      );
    }
    return teleportLocal(player, destination);
  }

  @Override
  public void complete(final TeleportCompletion completion) {
    TeleportCompletion checkedCompletion = Objects.requireNonNull(completion, "completion");
    CompletableFuture<TeleportResult> future = pending.remove(checkedCompletion.requestId());
    if (future != null) {
      future.complete(new TeleportResult(
          checkedCompletion.status(),
          checkedCompletion.message()
      ));
    }
  }

  @Override
  public void close() {
    TeleportResult shutdown = new TeleportResult(
        TeleportStatus.FAILED,
        "The server stopped before the teleport completed"
    );
    pending.values().forEach(future -> future.complete(shutdown));
    pending.clear();
  }

  private CompletableFuture<TeleportResult> teleportLocal(
      final Player player,
      final ServerPosition destination
  ) {
    CompletableFuture<TeleportResult> result = new CompletableFuture<>();
    player.getScheduler().run(plugin, task -> {
      if (!player.isConnected()) {
        result.complete(new TeleportResult(
            TeleportStatus.PLAYER_OFFLINE,
            "The player is no longer online"
        ));
        return;
      }
      Location location = worlds.createLocation(destination).orElse(null);
      if (location == null) {
        result.complete(new TeleportResult(
            TeleportStatus.WORLD_NOT_LOADED,
            "World " + destination.world().asString() + " is not loaded on this server"
        ));
        return;
      }
      player.teleportAsync(location).whenComplete((teleported, throwable) -> {
        if (throwable != null) {
          result.complete(new TeleportResult(
              TeleportStatus.FAILED,
              "The asynchronous teleport failed"
          ));
        } else if (Boolean.TRUE.equals(teleported)) {
          result.complete(TeleportResult.success());
        } else {
          result.complete(new TeleportResult(
              TeleportStatus.TELEPORT_REJECTED,
              "The teleport was rejected by the server"
          ));
        }
      });
    }, () -> result.complete(new TeleportResult(
        TeleportStatus.PLAYER_OFFLINE,
        "The player left before the teleport could start"
    )));
    return result;
  }

  private CompletableFuture<TeleportResult> transfer(
      final VexPlayer player,
      final ServerPosition destination
  ) {
    return playerData.save(player.getUniqueId()).handle((ignored, throwable) -> {
      if (throwable != null) {
        return completed(
            TeleportStatus.FAILED,
            "Player data could not be saved before the server transfer"
        );
      }
      return beginTransfer(player, destination);
    }).thenCompose(result -> result);
  }

  private CompletableFuture<TeleportResult> beginTransfer(
      final VexPlayer player,
      final ServerPosition destination
  ) {
    UUID requestId = UUID.randomUUID();
    CompletableFuture<TeleportResult> result = new CompletableFuture<>();
    pending.put(requestId, result);
    DeliveryResult delivery = messages.send(
        MessageTarget.proxy(),
        TeleportMessages.TRANSFER_REQUEST,
        new TeleportTransferRequest(
            requestId,
            player.getUniqueId(),
            destination,
            serverIdentity.getServerId().value()
        )
    );
    if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
      pending.remove(requestId);
      return completed(
          TeleportStatus.SERVER_UNAVAILABLE,
          "The proxy connection is not available"
      );
    }
    return result.completeOnTimeout(new TeleportResult(
          TeleportStatus.TIMED_OUT,
          "The cross-server teleport timed out"
        ), TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .whenComplete((ignored, throwable) -> pending.remove(requestId));
  }

  private CompletableFuture<TeleportResult> completed(
      final TeleportStatus status,
      final String message
  ) {
    return CompletableFuture.completedFuture(new TeleportResult(status, message));
  }
}
