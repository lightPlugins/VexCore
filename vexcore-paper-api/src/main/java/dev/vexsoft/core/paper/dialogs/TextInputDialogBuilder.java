package dev.vexsoft.core.paper.dialogs;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog that collects one text value from the player
 */
public interface TextInputDialogBuilder extends DialogBuilder<String, TextInputDialogBuilder> {

  /** Sets the label displayed beside the text field */
  TextInputDialogBuilder label(Component label);

  /** Sets the initial text field value */
  TextInputDialogBuilder initialValue(String value);

  /** Sets the maximum accepted text length */
  TextInputDialogBuilder maxLength(int maxLength);

  /** Enables a multiline field with the given line and height limits */
  TextInputDialogBuilder multiline(int maxLines, int height);

  /** Sets the label of the submit button */
  TextInputDialogBuilder submitButton(Component label);

  /** Sets the label of the cancellation button */
  TextInputDialogBuilder cancelButton(Component label);
}
