package dev.vexsoft.core.paper.scene.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Routes scene input and connection changes to the shared playback coordinator. */
@Dependencies(SceneCoordinatorService.class)
public final class VexSceneListener implements Listener {

    private final SceneCoordinatorService scenes;

    public VexSceneListener(final VexServiceRegistry services) {
        scenes = services.require(SceneCoordinatorService.class);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking() && scenes.reveal(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        scenes.interrupt(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        scenes.interrupt(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(final PlayerDeathEvent event) {
        scenes.interrupt(event.getEntity());
    }
}
