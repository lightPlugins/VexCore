package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.paper.dialogs.ConfirmationDialogBuilder;
import dev.vexsoft.core.paper.dialogs.NoticeDialogBuilder;
import dev.vexsoft.core.paper.dialogs.NumberRangeDialogBuilder;
import dev.vexsoft.core.paper.dialogs.TextInputDialogBuilder;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/**
 * Creates and manages player dialogs without exposing Paper's experimental API
 */
public interface DialogService extends VexService {

  /** Creates a notice dialog builder for the player */
  NoticeDialogBuilder notice(Player player);

  /** Creates a confirmation dialog builder for the player */
  ConfirmationDialogBuilder confirmation(Player player);

  /** Creates a text input dialog builder for the player */
  TextInputDialogBuilder textInput(Player player);

  /** Creates a number range dialog builder for the player */
  NumberRangeDialogBuilder numberRange(Player player);

  /** Closes the active dialog owned by this plugin for the player */
  void close(Player player);
}
