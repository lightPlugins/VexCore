package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.dialog.DialogBuilder;
import dev.vexsoft.core.dialog.DialogResult;
import dev.vexsoft.core.dialog.DialogResultType;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("UnstableApiUsage")
public abstract class AbstractDialogBuilder<T, B extends DialogBuilder<T, B>>
    implements DialogBuilder<T, B> {

  protected final ServiceOwner owner;
  protected final DialogCoordinatorService coordinator;
  protected final ScheduleService scheduler;
  protected final Player player;
  protected final List<DialogBody> body = new ArrayList<>();
  protected Component title = Component.text("VexCore");
  protected boolean canCloseWithEscape;
  protected Duration timeout = Duration.ofMinutes(5);

  protected AbstractDialogBuilder(
      final ServiceOwner owner,
      final DialogCoordinatorService coordinator,
      final ScheduleService scheduler,
      final Player player
  ) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.player = Objects.requireNonNull(player, "player");
  }

  @Override
  public B title(final Component title) {
    this.title = Objects.requireNonNull(title, "title");
    return self();
  }

  @Override
  public B message(final Component message) {
    body.add(DialogBody.plainMessage(Objects.requireNonNull(message, "message")));
    return self();
  }

  @Override
  public B item(final ItemStack item) {
    body.add(DialogBody.item(Objects.requireNonNull(item, "item").clone()).build());
    return self();
  }

  @Override
  public B canCloseWithEscape(final boolean canCloseWithEscape) {
    this.canCloseWithEscape = canCloseWithEscape;
    return self();
  }

  @Override
  public B timeout(final Duration timeout) {
    Duration checked = Objects.requireNonNull(timeout, "timeout");
    if (checked.isZero() || checked.isNegative()) {
      throw new IllegalArgumentException("timeout must be greater than zero");
    }
    this.timeout = checked;
    return self();
  }

  @Override
  public CompletableFuture<DialogResult<T>> open() {
    if (!player.isOnline()) {
      return CompletableFuture.completedFuture(DialogResult.empty(DialogResultType.UNAVAILABLE));
    }
    DialogSession<T> session = coordinator.begin(owner, player.getUniqueId());
    Dialog dialog;
    try {
      dialog = buildDialog(session);
    } catch (RuntimeException exception) {
      coordinator.complete(session, DialogResultType.UNAVAILABLE);
      throw exception;
    }

    session.setTimeoutTask(scheduler.runAsyncLater(timeout, () -> scheduler.runFor(
        player,
        () -> {
          if (coordinator.complete(session, DialogResultType.TIMED_OUT)) {
            player.closeDialog();
          }
        },
        () -> coordinator.complete(session, DialogResultType.PLAYER_LEFT)
    )));
    Optional<VexTask> showTask = scheduler.runFor(
        player,
        () -> {
          if (coordinator.isActive(session)) {
            player.showDialog(dialog);
          }
        },
        () -> coordinator.complete(session, DialogResultType.PLAYER_LEFT)
    );
    if (showTask.isEmpty()) {
      coordinator.complete(session, DialogResultType.PLAYER_LEFT);
    }
    return session.getFuture();
  }

  protected DialogBase base(final List<? extends DialogInput> inputs) {
    return DialogBase.builder(title)
        .canCloseWithEscape(canCloseWithEscape)
        .afterAction(DialogBase.DialogAfterAction.CLOSE)
        .body(body)
        .inputs(inputs)
        .build();
  }

  protected abstract B self();

  protected abstract Dialog buildDialog(DialogSession<T> session);
}
