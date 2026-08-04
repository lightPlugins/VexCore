package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.dialog.ConfirmationDialogBuilder;
import dev.vexsoft.core.dialog.DialogResult;
import dev.vexsoft.core.dialog.DialogResultType;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class VexConfirmationDialogBuilder
    extends AbstractDialogBuilder<Boolean, ConfirmationDialogBuilder>
    implements ConfirmationDialogBuilder {

  private Component confirmButton = Component.text("Confirm");
  private Component confirmTooltip;
  private Component cancelButton = Component.text("Cancel");
  private Component cancelTooltip;

  public VexConfirmationDialogBuilder(
      final ServiceOwner owner,
      final DialogCoordinatorService coordinator,
      final ScheduleService scheduler,
      final Player player
  ) {
    super(owner, coordinator, scheduler, player);
  }

  @Override
  public ConfirmationDialogBuilder confirmButton(final Component label) {
    confirmButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public ConfirmationDialogBuilder confirmTooltip(final Component tooltip) {
    confirmTooltip = Objects.requireNonNull(tooltip, "tooltip");
    return this;
  }

  @Override
  public ConfirmationDialogBuilder cancelButton(final Component label) {
    cancelButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public ConfirmationDialogBuilder cancelTooltip(final Component tooltip) {
    cancelTooltip = Objects.requireNonNull(tooltip, "tooltip");
    return this;
  }

  @Override
  protected ConfirmationDialogBuilder self() {
    return this;
  }

  @Override
  protected Dialog buildDialog(final DialogSession<Boolean> session) {
    return Dialog.create(factory -> factory.empty()
        .base(base(List.of()))
        .type(DialogType.confirmation(
            DialogActions.button(
                confirmButton,
                confirmTooltip,
                (response, audience) -> complete(session, true, DialogResultType.CONFIRMED)
            ),
            DialogActions.button(
                cancelButton,
                cancelTooltip,
                (response, audience) -> complete(session, false, DialogResultType.CANCELLED)
            )
        )));
  }

  private void complete(
      final DialogSession<Boolean> session,
      final boolean value,
      final DialogResultType type
  ) {
    scheduler.runFor(
        player,
        () -> coordinator.complete(session, DialogResult.value(type, value)),
        () -> coordinator.complete(session, DialogResultType.PLAYER_LEFT)
    );
  }
}
