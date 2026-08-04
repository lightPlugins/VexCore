package dev.vexsoft.core.dialog;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog that lets the player confirm or cancel an action
 */
public interface ConfirmationDialogBuilder extends DialogBuilder<Boolean, ConfirmationDialogBuilder> {

  /** Sets the label of the confirmation button */
  public ConfirmationDialogBuilder confirmButton(Component label);

  /** Sets the tooltip of the confirmation button */
  public ConfirmationDialogBuilder confirmTooltip(Component tooltip);

  /** Sets the label of the cancellation button */
  public ConfirmationDialogBuilder cancelButton(Component label);

  /** Sets the tooltip of the cancellation button */
  public ConfirmationDialogBuilder cancelTooltip(Component tooltip);
}
