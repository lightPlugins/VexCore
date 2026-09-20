package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Coordinates the single interactive session allowed for each viewer across plugin owners. */
public interface InteractiveUiCoordinatorService extends VexService, AutoCloseable {

    /** Opens an owner-bound session on the viewer's entity scheduler. */
    InteractiveUi open(ServiceOwner owner, Player player, String id);

    /** Looks up a matching owner and identifier without exposing another owner's session. */
    Optional<InteractiveUi> find(ServiceOwner owner, Player player, String id);

    /** Consumes session input from the central network handler, without accessing the world. */
    boolean consume(UUID viewerId, Object packet);

    /** Preserves the synthetic input target in outgoing world updates without reading the world. */
    Object preserveTarget(UUID viewerId, Object packet);

    /** Restores and retires a viewer before an external lifecycle transition. */
    void discard(Player player);

    /** Releases every session opened by the given plugin owner. */
    void closeOwner(ServiceOwner owner);

    /** Restores all sessions before schedulers and packet adapters stop. */
    @Override
    void close();
}
