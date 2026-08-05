package dev.vexsoft.core.dialog;

import net.kyori.adventure.text.Component;

/**
 * Builds a dialog containing one acknowledgement button
 */
public interface NoticeDialogBuilder extends DialogBuilder<Void, NoticeDialogBuilder> {

  /** Sets the label of the acknowledgement button */
  NoticeDialogBuilder button(Component label);

  /** Sets the optional tooltip of the acknowledgement button */
  NoticeDialogBuilder buttonTooltip(Component tooltip);
}
