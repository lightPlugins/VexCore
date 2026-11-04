package dev.vexsoft.core.paper.vexui.listener;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.vexui.session.VexUICoordinatorService;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Routes only VexUI custom actions and retires viewer sessions on disconnect. */
@Dependencies(VexUICoordinatorService.class)
@SuppressWarnings("UnstableApiUsage")
public final class VexUIListener implements Listener {

    private final VexUICoordinatorService coordinator;

    public VexUIListener(final VexServiceRegistry services) {
        coordinator = services.require(VexUICoordinatorService.class);
    }

    @EventHandler
    public void clicked(final PlayerCustomClickEvent event) {
        if (event.getIdentifier().namespace().equals("vexcore")
            && event.getIdentifier().value().startsWith("vexui/")
            && event.getCommonConnection() instanceof PlayerGameConnection game) {
            var response = event.getDialogResponseView();
            coordinator.dispatch(game.getPlayer(), event.getIdentifier(), response == null ? null : response.getText("query"));
        }
    }

    @EventHandler
    public void quit(final PlayerQuitEvent event) {
        coordinator.disconnected(event.getPlayer().getUniqueId());
    }
}
