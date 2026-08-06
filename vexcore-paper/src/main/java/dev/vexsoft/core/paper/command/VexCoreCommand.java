package dev.vexsoft.core.paper.command;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.paper.message.SendMessageService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandRoot(
    name = "vexcore",
    description = "Manages VexCore"
)
@Dependencies({
    LanguageService.class,
    LocalizationService.class,
    PlayerService.class,
    SendMessageService.class
})
public final class VexCoreCommand {

  private final LanguageService languages;
  private final LocalizationService localization;
  private final PlayerService players;
  private final SendMessageService messages;
  private final Logger logger;

  public VexCoreCommand(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    languages = services.require(LanguageService.class);
    localization = services.require(LocalizationService.class);
    players = services.require(PlayerService.class);
    messages = services.require(SendMessageService.class);
    logger = Logger.getLogger("VexCore");
  }

  @Command(
      value = "reload",
      permission = "vexcore.command.reload"
  )
  public int reload(final VexCommandSource source) {
    try {
      languages.reload();
      send(source.getSender(), "commands.vexcore.reload.success", Map.of());
      return 1;
    } catch (RuntimeException exception) {
      logger.log(Level.SEVERE, "Unable to reload VexCore localizations", exception);
      send(source.getSender(), "commands.vexcore.reload.failed", Map.of());
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
    languages.setLanguage(vexPlayer, selected.get().getKey());
    messages.send(
        player,
        "commands.vexcore.language.set",
        true,
        Map.of("language", selected.get().getKey().getValue())
    );
    return 1;
  }

  private void send(
      final CommandSender sender,
      final String key,
      final Map<String, String> replacements
  ) {
    if (sender instanceof Player player) {
      messages.send(player, key, true, replacements);
      return;
    }
    LocalizedMessage prefix = localization.resolve(LanguageKey.EN_EN, "general.prefix", Map.of());
    LocalizedMessage message = localization.resolve(LanguageKey.EN_EN, key, replacements);
    Component prefixComponent = prefix.getLines().getFirst();
    for (Component line : message.getLines()) {
      sender.sendMessage(prefixComponent.append(line));
    }
  }
}
