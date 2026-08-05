package dev.vexsoft.core.paper.packet.connection;

import org.bukkit.entity.Player;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.DisplayPacketAdapterService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import dev.vexsoft.core.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packet.hologram.HologramTrackerService;
import dev.vexsoft.core.paper.packet.hologram.TrackedHologram;

@Dependencies({
    PacketConnectionService.class,
    DisplayPacketAdapterService.class,
    HologramTrackerService.class
})
public final class VexPacketConnectionListener implements Listener {

  private final PacketConnectionService connections;
  private final DisplayPacketAdapterService displays;
  private final HologramTrackerService holograms;

  public VexPacketConnectionListener(final VexServiceRegistry services) {
    this.connections = services.require(PacketConnectionService.class);
    this.displays = services.require(DisplayPacketAdapterService.class);
    this.holograms = services.require(HologramTrackerService.class);
  }

  @EventHandler
  public void onJoin(final PlayerJoinEvent event) {
    connections.inject(event.getPlayer());
  }

  @EventHandler
  public void onQuit(final PlayerQuitEvent event) {
    connections.uninject(event.getPlayer());
    holograms.removeViewer(event.getPlayer().getUniqueId());
    displays.removeViewer(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(final PlayerDeathEvent event) {
    removeHolograms(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
    displays.removeViewer(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
  }

  @EventHandler
  public void onWorldChange(final PlayerChangedWorldEvent event) {
    removeHolograms(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
    displays.removeViewer(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
  }

  private void removeHolograms(
      final Player player,
      final DisplayLifecycle lifecycle
  ) {
    for (TrackedHologram hologram : holograms.removeViewer(player.getUniqueId(), lifecycle)) {
      displays.remove(
          player,
          hologram.getHandle().getTextDisplayEntityId(),
          hologram.getHandle().getInteractionEntityId()
      );
    }
  }
}
