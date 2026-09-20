package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Makes interactive sessions available to Vex plugins and closes them with their owner scope. */
@Dependencies(InteractiveUiCoordinatorService.class)
public final class VexInteractiveUiService implements InteractiveUiService, AutoCloseable {

    private final ServiceOwner owner;
    private final InteractiveUiCoordinatorService coordinator;
    private volatile boolean closed;

    public VexInteractiveUiService(VexServiceRegistry services) {
        owner = services.getOwner();
        coordinator = services.require(InteractiveUiCoordinatorService.class);
    }

    @Override
    public InteractiveUi open(Player player, String id) {
        if (closed) {
            throw new IllegalStateException("Interactive UI owner is closed");
        }
        InteractiveUi session = coordinator.open(owner, player, id);

        // Owner shutdown can race setup on another region; do not return a session beyond its owner's lifetime.
        if (closed) {
            session.close();
            throw new IllegalStateException("Interactive UI owner closed during setup");
        }
        return session;
    }

    @Override
    public Optional<InteractiveUi> find(Player player, String id) {
        return coordinator.find(owner, player, id);
    }

    @Override
    public void close() {
        closed = true;
        coordinator.closeOwner(owner);
    }
}
