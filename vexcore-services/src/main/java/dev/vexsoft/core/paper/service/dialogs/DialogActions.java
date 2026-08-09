package dev.vexsoft.core.paper.service.dialogs;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

@SuppressWarnings("UnstableApiUsage")
public final class DialogActions {

  private static final int DEFAULT_BUTTON_WIDTH = 150;

  private DialogActions() { }

  public static ActionButton button(
      final Component label,
      final Component tooltip,
      final DialogActionCallback callback
  ) {
    return ActionButton.create(
        label,
        tooltip,
        DEFAULT_BUTTON_WIDTH,
        DialogAction.customClick(
            callback,
            ClickCallback.Options.builder().uses(1).build()
        )
    );
  }
}
