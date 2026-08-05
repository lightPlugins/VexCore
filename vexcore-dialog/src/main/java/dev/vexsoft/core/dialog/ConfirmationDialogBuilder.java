package dev.vexsoft.core.dialog;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog that lets the player confirm or cancel an action
 */
public interface ConfirmationDialogBuilder extends DialogBuilder<Boolean, ConfirmationDialogBuilder> {

  /** Sets the label of the confirmation button */
  ConfirmationDialogBuilder confirmButton(Component label);

  /** Sets the tooltip of the confirmation button */
  ConfirmationDialogBuilder confirmTooltip(Component tooltip);

  /** Sets the label of the cancellation button */
  ConfirmationDialogBuilder cancelButton(Component label);

  /** Sets the tooltip of the cancellation button */
  ConfirmationDialogBuilder cancelTooltip(Component tooltip);
}
