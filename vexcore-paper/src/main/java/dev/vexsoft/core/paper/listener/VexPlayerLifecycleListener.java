package dev.vexsoft.core.paper.listener;

import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public final class VexPlayerLifecycleListener implements Listener {

  @NonNull
  private final PlayerDataCoordinatorService players;
  @NonNull
  private final Logger logger;

  @EventHandler
  public void onPlayerPreLogin(final AsyncPlayerPreLoginEvent event) {
    try {
      players.load(event.getUniqueId(), event.getName()).join();
    } catch (RuntimeException exception) {
      logger.log(Level.SEVERE, "Unable to load VexPlayer " + event.getUniqueId(), exception);
      event.disallow(
          AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
          net.kyori.adventure.text.Component.text("Your player data could not be loaded")
      );
    }
  }

  @EventHandler
  public void onPlayerJoin(final PlayerJoinEvent event) {
    players.create(event.getPlayer().getUniqueId(), event.getPlayer().getName());
  }

  @EventHandler
  public void onPlayerQuit(final PlayerQuitEvent event) {
    players.saveAndRemove(event.getPlayer().getUniqueId()).exceptionally(throwable -> {
      logger.log(Level.SEVERE, "Unable to save VexPlayer " + event.getPlayer().getUniqueId(), throwable);
      return null;
    });
  }
}
