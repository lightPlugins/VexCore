package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Dependencies(ScreenUiCoordinatorService.class)
public final class VexScreenUiListener implements Listener {

    private final ScreenUiCoordinatorService screens;

    public VexScreenUiListener(VexServiceRegistry services) {
        screens = services.require(ScreenUiCoordinatorService.class);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        screens.discard(event.getPlayer());
    }
}
