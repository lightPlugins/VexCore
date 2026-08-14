package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.localization.editor.LocalizationEditorUiService;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Opens the VexCore localization editor. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies(LocalizationEditorUiService.class)
public final class VexCoreLocalizationCommand {

  private final LocalizationEditorUiService editor;

  public VexCoreLocalizationCommand(final VexServiceRegistry services) {
    editor = Objects.requireNonNull(services, "services")
        .require(LocalizationEditorUiService.class);
  }

  /** Opens the inventory-based localization editor. */
  @Command(
      value = "localization",
      permission = "vexcore.command.localization",
      playerOnly = true
  )
  public int open(final VexCommandSource source) {
    editor.open((Player) source.getSender());
    return 1;
  }
}
