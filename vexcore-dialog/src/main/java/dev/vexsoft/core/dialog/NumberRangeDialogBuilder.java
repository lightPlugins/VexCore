package dev.vexsoft.core.dialog;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog that collects one number from a configured range
 */
public interface NumberRangeDialogBuilder extends DialogBuilder<Float, NumberRangeDialogBuilder> {

  /** Sets the label displayed beside the number slider */
  public NumberRangeDialogBuilder label(Component label);

  /** Sets the minimum and maximum slider values */
  public NumberRangeDialogBuilder range(float minimum, float maximum);

  /** Sets the initial slider value */
  public NumberRangeDialogBuilder initialValue(float value);

  /** Sets the distance between selectable slider values */
  public NumberRangeDialogBuilder step(float step);

  /** Sets the Minecraft format used for the slider label */
  public NumberRangeDialogBuilder labelFormat(String format);

  /** Sets the label of the submit button */
  public NumberRangeDialogBuilder submitButton(Component label);

  /** Sets the label of the cancellation button */
  public NumberRangeDialogBuilder cancelButton(Component label);
}
