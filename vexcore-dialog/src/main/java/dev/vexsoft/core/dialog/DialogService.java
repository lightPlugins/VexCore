package dev.vexsoft.core.dialog;

import dev.vexsoft.core.api.service.VexService;
import org.bukkit.entity.Player;

/**
 * Creates and manages player dialogs without exposing Paper's experimental API
 */
public interface DialogService extends VexService {

  /** Creates a notice dialog builder for the player */
  public NoticeDialogBuilder notice(Player player);

  /** Creates a confirmation dialog builder for the player */
  public ConfirmationDialogBuilder confirmation(Player player);

  /** Creates a text input dialog builder for the player */
  public TextInputDialogBuilder textInput(Player player);

  /** Creates a number range dialog builder for the player */
  public NumberRangeDialogBuilder numberRange(Player player);

  /** Closes the active dialog owned by this plugin for the player */
  public void close(Player player);
}
