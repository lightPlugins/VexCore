package dev.vexsoft.core.paper.command;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.theme.ThemeColorService;
import dev.vexsoft.core.paper.message.SendMessageService;
import dev.vexsoft.core.messaging.debug.ProxyDebugMessages;
import dev.vexsoft.core.messaging.debug.ProxyPingRequest;
import dev.vexsoft.core.paper.messaging.ProxyPingService;
import dev.vexsoft.core.paper.performance.PerformanceBossBarService;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

@CommandRoot(
    name = "vexcore",
    description = "Manages VexCore"
)
@Dependencies({
    LanguageService.class,
    PlayerService.class,
    SendMessageService.class,
    MessagingService.class,
    ProxyPingService.class,
    PerformanceBossBarService.class,
    ThemeColorService.class
})
public final class VexCoreCommand {

  private final LanguageService languages;
  private final PlayerService players;
  private final SendMessageService messages;
  private final MessagingService messaging;
  private final ProxyPingService proxyPings;
  private final PerformanceBossBarService performanceBossBars;
  private final ThemeColorService themeColors;
  private final Logger logger;

  public VexCoreCommand(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    languages = services.require(LanguageService.class);
    players = services.require(PlayerService.class);
    messages = services.require(SendMessageService.class);
    messaging = services.require(MessagingService.class);
    proxyPings = services.require(ProxyPingService.class);
    performanceBossBars = services.require(PerformanceBossBarService.class);
    themeColors = services.require(ThemeColorService.class);
    logger = Logger.getLogger("VexCore");
  }

  @Command(
      value = "reload",
      permission = "vexcore.command.reload"
  )
  public int reload(final VexCommandSource source) {
    try {
      themeColors.reload();
      languages.reload();
      messages.send(source.getSender(), "commands.vexcore.reload.success", true);
      return 1;
    } catch (RuntimeException exception) {
      logger.log(Level.SEVERE, "Unable to reload VexCore configuration and localizations", exception);
      messages.send(source.getSender(), "commands.vexcore.reload.failed", true);
      return 0;
    }
  }

  @Command(
      value = "language set <language>",
      permission = "vexcore.command.language",
      playerOnly = true
  )
  public int setLanguage(
      final VexCommandSource source,
      @Argument("language") @Suggest(LanguageSuggestionProvider.class) final String language
  ) {
    Player player = (Player) source.getSender();
    Optional<Language> selected = languages.findLanguage(language);
    if (selected.isEmpty()) {
      messages.send(
          player,
          "commands.vexcore.language.not-found",
          true,
          Map.of("language", language)
      );
      return 0;
    }

    VexPlayer vexPlayer = players.require(player.getUniqueId());
    vexPlayer.getContainer(LanguageContainer.class).setLanguage(selected.get().getKey());
    messages.send(
        player,
        "commands.vexcore.language.set",
        true,
        Map.of("language", selected.get().getKey().getValue())
    );
    return 1;
  }

  @Command(
      value = "debug proxy ping",
      permission = "vexcore.command.debug.proxy.ping",
      playerOnly = true
  )
  public int proxyPing(final VexCommandSource source) {
    Player player = (Player) source.getSender();
    UUID requestId = proxyPings.begin(player);
    DeliveryResult result = messaging.send(
        MessageTarget.proxy(),
        ProxyDebugMessages.PING_REQUEST,
        new ProxyPingRequest(requestId)
    );
    if (result == DeliveryResult.SENT || result == DeliveryResult.QUEUED) {
      messages.send(player, "commands.vexcore.debug.proxy.ping.started", true);
      return 1;
    }
    proxyPings.cancel(requestId);
    messages.send(
        player,
        "commands.vexcore.debug.proxy.ping.failed",
        true,
        Map.of("reason", result.name().toLowerCase(Locale.ROOT))
    );
    return 0;
  }

  @Command(
      value = "debug performance toggle",
      permission = "vexcore.command.debug.performance",
      playerOnly = true
  )
  public int togglePerformance(final VexCommandSource source) {
    Player player = (Player) source.getSender();
    boolean visible = performanceBossBars.toggle(player);
    messages.send(
        player,
        visible
            ? "commands.vexcore.debug.performance.enabled"
            : "commands.vexcore.debug.performance.disabled",
        true
    );
    return 1;
  }

}
