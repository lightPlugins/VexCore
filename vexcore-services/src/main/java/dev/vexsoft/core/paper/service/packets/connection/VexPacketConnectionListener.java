package dev.vexsoft.core.paper.service.packets.connection;

import org.bukkit.entity.Player;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.service.packets.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.service.packets.interaction.TrackedInteraction;

@Dependencies({
    PacketConnectionService.class,
    DisplayPacketAdapterService.class,
    InteractionTrackerService.class
})
public final class VexPacketConnectionListener implements Listener {

  private final PacketConnectionService connections;
  private final DisplayPacketAdapterService displays;
  private final InteractionTrackerService interactions;

  public VexPacketConnectionListener(final VexServiceRegistry services) {
    this.connections = services.require(PacketConnectionService.class);
    this.displays = services.require(DisplayPacketAdapterService.class);
    this.interactions = services.require(InteractionTrackerService.class);
  }

  @EventHandler
  public void onJoin(final PlayerJoinEvent event) {
    connections.inject(event.getPlayer());
  }

  @EventHandler
  public void onQuit(final PlayerQuitEvent event) {
    connections.uninject(event.getPlayer());
    interactions.removeViewer(event.getPlayer().getUniqueId());
    displays.removeViewer(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(final PlayerDeathEvent event) {
    removeInteractions(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
    displays.removeViewer(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
  }

  @EventHandler
  public void onWorldChange(final PlayerChangedWorldEvent event) {
    removeInteractions(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
    displays.removeViewer(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
  }

  private void removeInteractions(
      final Player player,
      final DisplayLifecycle lifecycle
  ) {
    for (TrackedInteraction interaction
        : interactions.removeViewer(player.getUniqueId(), lifecycle)) {
      displays.remove(
          player,
          interaction.getHandle().getEntityId()
      );
    }
  }
}
