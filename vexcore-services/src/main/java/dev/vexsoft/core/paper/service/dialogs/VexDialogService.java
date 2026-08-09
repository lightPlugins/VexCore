package dev.vexsoft.core.paper.service.dialogs;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.dialogs.ConfirmationDialogBuilder;
import dev.vexsoft.core.paper.dialogs.DialogResultType;
import dev.vexsoft.core.paper.dialogs.NoticeDialogBuilder;
import dev.vexsoft.core.paper.dialogs.NumberRangeDialogBuilder;
import dev.vexsoft.core.paper.dialogs.TextInputDialogBuilder;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Objects;
import org.bukkit.entity.Player;

@Dependencies({DialogCoordinatorService.class, ScheduleService.class})
public final class VexDialogService implements DialogService, AutoCloseable {

  private final ServiceOwner owner;
  private final DialogCoordinatorService coordinator;
  private final ScheduleService scheduler;

  public VexDialogService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    coordinator = checkedServices.require(DialogCoordinatorService.class);
    scheduler = checkedServices.require(ScheduleService.class);
  }

  @Override
  public NoticeDialogBuilder notice(final Player player) {
    return new VexNoticeDialogBuilder(owner, coordinator, scheduler, player);
  }

  @Override
  public ConfirmationDialogBuilder confirmation(final Player player) {
    return new VexConfirmationDialogBuilder(owner, coordinator, scheduler, player);
  }

  @Override
  public TextInputDialogBuilder textInput(final Player player) {
    return new VexTextInputDialogBuilder(owner, coordinator, scheduler, player);
  }

  @Override
  public NumberRangeDialogBuilder numberRange(final Player player) {
    return new VexNumberRangeDialogBuilder(owner, coordinator, scheduler, player);
  }

  @Override
  public void close(final Player player) {
    Player checkedPlayer = Objects.requireNonNull(player, "player");
    if (coordinator.close(owner, checkedPlayer.getUniqueId(), DialogResultType.CLOSED)) {
      scheduler.runFor(checkedPlayer, checkedPlayer::closeDialog);
    }
  }

  @Override
  public void close() {
    coordinator.closeOwned(owner, DialogResultType.PLUGIN_DISABLED);
  }
}
