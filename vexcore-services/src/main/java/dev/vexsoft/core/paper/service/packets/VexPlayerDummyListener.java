package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.PlayerDummyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

@Dependencies(PlayerDummyService.class)
public final class VexPlayerDummyListener implements Listener {
  private final PlayerDummyService dummies;

  public VexPlayerDummyListener(VexServiceRegistry services) {
    dummies = services.require(PlayerDummyService.class);
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    dummies.removeAll(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void world(PlayerChangedWorldEvent event) {
    dummies.removeAll(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void respawn(PlayerRespawnEvent event) {
    dummies.removeAll(event.getPlayer().getUniqueId());
  }
}
