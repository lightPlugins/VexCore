package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.dialog.DialogResultType;
import dev.vexsoft.core.dialog.NoticeDialogBuilder;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class VexNoticeDialogBuilder
    extends AbstractDialogBuilder<Void, NoticeDialogBuilder>
    implements NoticeDialogBuilder {

  private Component button = Component.text("Done");
  private Component buttonTooltip;

  public VexNoticeDialogBuilder(
      final ServiceOwner owner,
      final DialogCoordinatorService coordinator,
      final ScheduleService scheduler,
      final Player player
  ) {
    super(owner, coordinator, scheduler, player);
  }

  @Override
  public NoticeDialogBuilder button(final Component label) {
    button = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public NoticeDialogBuilder buttonTooltip(final Component tooltip) {
    buttonTooltip = Objects.requireNonNull(tooltip, "tooltip");
    return this;
  }

  @Override
  protected NoticeDialogBuilder self() {
    return this;
  }

  @Override
  protected Dialog buildDialog(final DialogSession<Void> session) {
    return Dialog.create(factory -> factory.empty()
        .base(base(List.of()))
        .type(DialogType.notice(DialogActions.button(
            button,
            buttonTooltip,
            (response, audience) -> scheduler.runFor(
                player,
                () -> coordinator.complete(session, DialogResultType.CONFIRMED)
            )
        ))));
  }
}
