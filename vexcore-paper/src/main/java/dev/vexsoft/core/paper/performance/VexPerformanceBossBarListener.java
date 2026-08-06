package dev.vexsoft.core.paper.performance;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Dependencies({PerformanceBossBarService.class})
public final class VexPerformanceBossBarListener implements Listener {

  private final PerformanceBossBarService bossBars;

  public VexPerformanceBossBarListener(final VexServiceRegistry services) {
    bossBars = Objects.requireNonNull(services, "services")
        .require(PerformanceBossBarService.class);
  }

  @EventHandler
  public void onPlayerQuit(final PlayerQuitEvent event) {
    bossBars.hide(event.getPlayer());
  }
}
