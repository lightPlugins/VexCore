package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.dialogs.DialogResult;
import dev.vexsoft.core.paper.dialogs.DialogResultType;
import dev.vexsoft.core.paper.dialogs.TextInputDialogBuilder;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class VexTextInputDialogBuilder
    extends AbstractDialogBuilder<String, TextInputDialogBuilder>
    implements TextInputDialogBuilder {

  private static final String INPUT_KEY = "value";

  private Component label = Component.text("Value");
  private String initialValue = "";
  private int maxLength = 128;
  private TextDialogInput.MultilineOptions multiline;
  private Component submitButton = Component.text("Submit");
  private Component cancelButton = Component.text("Cancel");

  public VexTextInputDialogBuilder(
      final ServiceOwner owner,
      final DialogCoordinatorService coordinator,
      final ScheduleService scheduler,
      final Player player
  ) {
    super(owner, coordinator, scheduler, player);
  }

  @Override
  public TextInputDialogBuilder label(final Component label) {
    this.label = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public TextInputDialogBuilder initialValue(final String value) {
    initialValue = Objects.requireNonNull(value, "value");
    return this;
  }

  @Override
  public TextInputDialogBuilder maxLength(final int maxLength) {
    if (maxLength < 1) {
      throw new IllegalArgumentException("maxLength must be greater than zero");
    }
    this.maxLength = maxLength;
    return this;
  }

  @Override
  public TextInputDialogBuilder multiline(final int maxLines, final int height) {
    if (maxLines < 1) {
      throw new IllegalArgumentException("maxLines must be greater than zero");
    }
    if (height < 1 || height > 512) {
      throw new IllegalArgumentException("height must be between 1 and 512");
    }
    multiline = TextDialogInput.MultilineOptions.create(maxLines, height);
    return this;
  }

  @Override
  public TextInputDialogBuilder submitButton(final Component label) {
    submitButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public TextInputDialogBuilder cancelButton(final Component label) {
    cancelButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  protected TextInputDialogBuilder self() {
    return this;
  }

  @Override
  protected Dialog buildDialog(final DialogSession<String> session) {
    TextDialogInput input = DialogInput.text(INPUT_KEY, label)
        .initial(initialValue)
        .maxLength(maxLength)
        .multiline(multiline)
        .width(300)
        .build();
    return Dialog.create(factory -> factory.empty()
        .base(base(List.of(input)))
        .type(DialogType.confirmation(
            DialogActions.button(
                submitButton,
                null,
                (response, audience) -> submit(session, response.getText(INPUT_KEY))
            ),
            DialogActions.button(
                cancelButton,
                null,
                (response, audience) -> scheduler.runFor(
                    player,
                    () -> coordinator.complete(session, DialogResultType.CANCELLED)
                )
            )
        )));
  }

  private void submit(final DialogSession<String> session, final String value) {
    scheduler.runFor(
        player,
        () -> {
          if (value == null || value.length() > maxLength) {
            coordinator.complete(session, DialogResultType.UNAVAILABLE);
            return;
          }
          coordinator.complete(
              session,
              DialogResult.value(DialogResultType.CONFIRMED, value)
          );
        },
        () -> coordinator.complete(session, DialogResultType.PLAYER_LEFT)
    );
  }
}
