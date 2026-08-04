package dev.vexsoft.core.paper.dialog;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.dialog.DialogResultType;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Dependencies({DialogCoordinatorService.class})
public final class VexDialogListener implements Listener {

  private final DialogCoordinatorService dialogs;

  public VexDialogListener(final VexServiceRegistry services) {
    dialogs = Objects.requireNonNull(services, "services").require(DialogCoordinatorService.class);
  }

  @EventHandler
  public void onPlayerQuit(final PlayerQuitEvent event) {
    dialogs.closePlayer(event.getPlayer().getUniqueId(), DialogResultType.PLAYER_LEFT);
  }
}
