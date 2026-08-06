package dev.vexsoft.core.paper.listener;

import net.kyori.adventure.text.Component;
import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.api.signal.SignalService;
import dev.vexsoft.core.api.signal.core.PlayerDataLoadedSignal;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Dependencies({PlayerDataCoordinatorService.class, SignalService.class})
public final class VexPlayerLifecycleListener implements Listener {

  private final PlayerDataCoordinatorService players;
  private final SignalService signals;
  private final Logger logger;

  public VexPlayerLifecycleListener(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    players = checkedServices.require(PlayerDataCoordinatorService.class);
    signals = checkedServices.require(SignalService.class);
    if (!(checkedServices.getOwner() instanceof Plugin plugin)) {
      throw new IllegalArgumentException("Player lifecycle listener owner must be a Bukkit plugin");
    }
    logger = plugin.getLogger();
  }

  @EventHandler
  public void onPlayerPreLogin(final AsyncPlayerPreLoginEvent event) {
    try {
      players.load(event.getUniqueId(), event.getName()).join();
    } catch (RuntimeException exception) {
      logger.log(Level.SEVERE, "Unable to load VexPlayer " + event.getUniqueId(), exception);
      event.disallow(
          AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
          Component.text("Your player data could not be loaded")
      );
    }
  }

  @EventHandler
  public void onPlayerJoin(final PlayerJoinEvent event) {
    players.find(event.getPlayer().getUniqueId()).orElseThrow(() -> new IllegalStateException(
        "VexPlayer was not loaded before join: " + event.getPlayer().getUniqueId()
    ));
    signals.publish(new PlayerDataLoadedSignal(
        event.getPlayer().getUniqueId(),
        event.getPlayer().getName()
    ));
  }

  @EventHandler
  public void onPlayerQuit(final PlayerQuitEvent event) {
    players.saveAndRemove(event.getPlayer().getUniqueId()).exceptionally(throwable -> {
      logger.log(Level.SEVERE, "Unable to save VexPlayer " + event.getPlayer().getUniqueId(), throwable);
      return null;
    });
  }
}
