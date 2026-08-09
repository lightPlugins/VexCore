package dev.vexsoft.core.paper.command;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Destructive player-container reset commands below the shared VexCore root. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({PlayerDataCoordinatorService.class, SendMessageService.class})
public final class VexCoreResetCommand {

  private final PlayerDataCoordinatorService players;
  private final SendMessageService messages;
  private final Logger logger = Logger.getLogger("VexCore");

  public VexCoreResetCommand(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    players = checkedServices.require(PlayerDataCoordinatorService.class);
    messages = checkedServices.require(SendMessageService.class);
  }

  /** Resets one player's selected persistent container. */
  @Command(
      value = "reset player <player> container <container> confirm",
      permission = "vexcore.command.reset.player.container"
  )
  public int resetPlayerContainer(
      final VexCommandSource source,
      @Argument("player") final String player,
      @Argument("container") @Suggest(ContainerSuggestionProvider.class) final String container
  ) {
    return resetPlayer(
        source,
        player,
        uniqueId -> players.resetPlayerContainer(uniqueId, container),
        "commands.vexcore.reset.player-container.success",
        Map.of("player", player, "container", container)
    );
  }

  /** Resets all persistent containers belonging to one player. */
  @Command(
      value = "reset player <player> all confirm",
      permission = "vexcore.command.reset.player.all"
  )
  public int resetPlayerAll(
      final VexCommandSource source,
      @Argument("player") final String player
  ) {
    return resetPlayer(
        source,
        player,
        players::resetPlayerContainers,
        "commands.vexcore.reset.player-all.success",
        Map.of("player", player)
    );
  }

  /** Resets one persistent container for every stored player. */
  @Command(
      value = "reset global container <container> confirm",
      permission = "vexcore.command.reset.global.container"
  )
  public int resetGlobalContainer(
      final VexCommandSource source,
      @Argument("container") @Suggest(ContainerSuggestionProvider.class) final String container
  ) {
    run(
        source,
        players.resetGlobalContainer(container),
        "commands.vexcore.reset.global-container.success",
        Map.of("container", container)
    );
    return 1;
  }

  /** Resets every persistent container for every stored player. */
  @Command(
      value = "reset global all confirm",
      permission = "vexcore.command.reset.global.all"
  )
  public int resetGlobalAll(final VexCommandSource source) {
    run(
        source,
        players.resetGlobalContainers(),
        "commands.vexcore.reset.global-all.success",
        Map.of()
    );
    return 1;
  }

  private int resetPlayer(
      final VexCommandSource source,
      final String player,
      final Function<UUID, CompletableFuture<Void>> reset,
      final String successKey,
      final Map<String, String> replacements
  ) {
    players.resolveUniqueId(player).thenAccept(uniqueId -> {
      if (uniqueId.isEmpty()) {
        messages.send(
            source.getSender(),
            "commands.vexcore.reset.player-not-found",
            true,
            Map.of("player", player)
        );
        return;
      }
      run(source, reset.apply(uniqueId.get()), successKey, replacements);
    }).exceptionally(throwable -> {
      reportFailure(source, throwable);
      return null;
    });
    return 1;
  }

  private void run(
      final VexCommandSource source,
      final CompletableFuture<Void> operation,
      final String successKey,
      final Map<String, String> replacements
  ) {
    operation.whenComplete((ignored, throwable) -> {
      if (throwable != null) {
        reportFailure(source, throwable);
      } else {
        messages.send(source.getSender(), successKey, true, replacements);
      }
    });
  }

  private void reportFailure(final VexCommandSource source, final Throwable throwable) {
    logger.log(Level.SEVERE, "Unable to reset VexCore player data", throwable);
    messages.send(source.getSender(), "commands.vexcore.reset.failed", true);
  }
}
