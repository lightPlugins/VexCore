package dev.vexsoft.core.paper.service.packets.connection;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.service.packets.interaction.InteractionTrackerService;
import dev.vexsoft.core.paper.service.packets.interaction.TrackedInteraction;
import dev.vexsoft.core.paper.service.interactiveui.InteractiveUiCoordinatorService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

@Dependencies({PacketConnectionService.class, DisplayPacketAdapterService.class, InteractionTrackerService.class,
    InteractiveUiCoordinatorService.class})
public final class VexPacketConnectionListener implements Listener {

    private final PacketConnectionService connections;
    private final DisplayPacketAdapterService displays;
    private final InteractionTrackerService interactions;
    private final InteractiveUiCoordinatorService interactiveUi;

    public VexPacketConnectionListener(final VexServiceRegistry services) {
        this.connections = services.require(PacketConnectionService.class);
        this.displays = services.require(DisplayPacketAdapterService.class);
        this.interactions = services.require(InteractionTrackerService.class);
        this.interactiveUi = services.require(InteractiveUiCoordinatorService.class);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        connections.inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        interactiveUi.discard(event.getPlayer());
        connections.uninject(event.getPlayer());
        interactions.removeViewer(event.getPlayer().getUniqueId());
        displays.removeViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        interactiveUi.discard(event.getPlayer());
        removeInteractions(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
        displays.removeViewer(event.getPlayer(), DisplayLifecycle.PLAYER_DEATH);
    }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        interactiveUi.discard(event.getPlayer());
        removeInteractions(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
        displays.removeViewer(event.getPlayer(), DisplayLifecycle.WORLD_CHANGE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        interactiveUi.discard(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        interactiveUi.discard(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            interactiveUi.discard(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            interactiveUi.discard(player);
        }
    }

    private void removeInteractions(final Player player, final DisplayLifecycle lifecycle) {
        for (TrackedInteraction interaction : interactions.removeViewer(player.getUniqueId(), lifecycle)) {
            displays.remove(player, interaction.getHandle().getEntityId());
        }
    }
}
