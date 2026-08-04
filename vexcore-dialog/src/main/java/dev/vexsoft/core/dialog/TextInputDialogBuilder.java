package dev.vexsoft.core.dialog;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog that collects one text value from the player
 */
public interface TextInputDialogBuilder extends DialogBuilder<String, TextInputDialogBuilder> {

  /** Sets the label displayed beside the text field */
  public TextInputDialogBuilder label(Component label);

  /** Sets the initial text field value */
  public TextInputDialogBuilder initialValue(String value);

  /** Sets the maximum accepted text length */
  public TextInputDialogBuilder maxLength(int maxLength);

  /** Enables a multiline field with the given line and height limits */
  public TextInputDialogBuilder multiline(int maxLines, int height);

  /** Sets the label of the submit button */
  public TextInputDialogBuilder submitButton(Component label);

  /** Sets the label of the cancellation button */
  public TextInputDialogBuilder cancelButton(Component label);
}
