package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.localization.LanguageService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.Argument;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.Suggest;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Player language commands below the shared VexCore root. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({LanguageService.class, PlayerService.class, SendMessageService.class})
public final class VexCoreLanguageCommand {

  private final LanguageService languages;
  private final PlayerService players;
  private final SendMessageService messages;

  public VexCoreLanguageCommand(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    languages = checkedServices.require(LanguageService.class);
    players = checkedServices.require(PlayerService.class);
    messages = checkedServices.require(SendMessageService.class);
  }

  /** Changes the language selected by the executing player. */
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
}
