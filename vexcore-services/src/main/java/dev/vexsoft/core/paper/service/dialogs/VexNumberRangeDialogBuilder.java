package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.dialogs.DialogResult;
import dev.vexsoft.core.paper.dialogs.DialogResultType;
import dev.vexsoft.core.paper.dialogs.NumberRangeDialogBuilder;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class VexNumberRangeDialogBuilder
    extends AbstractDialogBuilder<Float, NumberRangeDialogBuilder>
    implements NumberRangeDialogBuilder {

  private static final String INPUT_KEY = "value";

  private Component label = Component.text("Value");
  private float minimum;
  private float maximum = 100.0F;
  private Float initialValue;
  private Float step;
  private String labelFormat = "options.generic_value";
  private Component submitButton = Component.text("Submit");
  private Component cancelButton = Component.text("Cancel");

  public VexNumberRangeDialogBuilder(
      final ServiceOwner owner,
      final DialogCoordinatorService coordinator,
      final ScheduleService scheduler,
      final Player player
  ) {
    super(owner, coordinator, scheduler, player);
  }

  @Override
  public NumberRangeDialogBuilder label(final Component label) {
    this.label = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public NumberRangeDialogBuilder range(final float minimum, final float maximum) {
    if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum >= maximum) {
      throw new IllegalArgumentException("minimum must be smaller than maximum");
    }
    this.minimum = minimum;
    this.maximum = maximum;
    return this;
  }

  @Override
  public NumberRangeDialogBuilder initialValue(final float value) {
    requireFinite(value, "value");
    initialValue = value;
    return this;
  }

  @Override
  public NumberRangeDialogBuilder step(final float step) {
    if (!Float.isFinite(step) || step <= 0.0F) {
      throw new IllegalArgumentException("step must be greater than zero");
    }
    this.step = step;
    return this;
  }

  @Override
  public NumberRangeDialogBuilder labelFormat(final String format) {
    labelFormat = Objects.requireNonNull(format, "format");
    return this;
  }

  @Override
  public NumberRangeDialogBuilder submitButton(final Component label) {
    submitButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  public NumberRangeDialogBuilder cancelButton(final Component label) {
    cancelButton = Objects.requireNonNull(label, "label");
    return this;
  }

  @Override
  protected NumberRangeDialogBuilder self() {
    return this;
  }

  @Override
  protected Dialog buildDialog(final DialogSession<Float> session) {
    validateSelection();
    NumberRangeDialogInput input = DialogInput.numberRange(INPUT_KEY, label, minimum, maximum)
        .initial(initialValue)
        .step(step)
        .labelFormat(labelFormat)
        .width(300)
        .build();
    return Dialog.create(factory -> factory.empty()
        .base(base(List.of(input)))
        .type(DialogType.confirmation(
            DialogActions.button(
                submitButton,
                null,
                (response, audience) -> submit(session, response.getFloat(INPUT_KEY))
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

  private void submit(final DialogSession<Float> session, final Float value) {
    scheduler.runFor(
        player,
        () -> {
          if (value == null || !Float.isFinite(value) || value < minimum || value > maximum) {
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

  private void validateSelection() {
    if (initialValue != null && (initialValue < minimum || initialValue > maximum)) {
      throw new IllegalStateException("initialValue must be inside the configured range");
    }
  }

  private void requireFinite(final float value, final String name) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }
}
