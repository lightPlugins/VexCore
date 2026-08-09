package dev.vexsoft.core.paper.command;

import dev.vexsoft.core.api.service.localization.LanguageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.localization.ThemeColorService;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** General VexCore administration commands. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({LanguageService.class, SendMessageService.class, ThemeColorService.class})
public final class VexCoreCommand {

  private final LanguageService languages;
  private final SendMessageService messages;
  private final ThemeColorService themeColors;
  private final Logger logger = Logger.getLogger("VexCore");

  public VexCoreCommand(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    languages = checkedServices.require(LanguageService.class);
    messages = checkedServices.require(SendMessageService.class);
    themeColors = checkedServices.require(ThemeColorService.class);
  }

  /** Reloads VexCore configuration and localization resources. */
  @Command(value = "reload", permission = "vexcore.command.reload")
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
}
