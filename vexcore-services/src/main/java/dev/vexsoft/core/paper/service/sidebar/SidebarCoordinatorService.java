package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Coordinates sidebar channels across every owner-scoped Vex plugin. */
public interface SidebarCoordinatorService extends VexService {

    /** Sets or replaces a persistent sidebar channel belonging to the supplied owner. */
    void setPersistent(ServiceOwner owner, Player player, String channel, SidebarFrame frame, int priority);

    /** Shows an owner's temporary sidebar channel for the requested duration in ticks. */
    void showTemporary(
        ServiceOwner owner,
        Player player,
        String channel,
        SidebarFrame frame,
        long durationTicks,
        int priority
    );

    /** Removes the owner's persistent channel for the player. */
    void clearPersistent(ServiceOwner owner, Player player, String channel);

    /** Removes the owner's temporary channel for the player. */
    void clearTemporary(ServiceOwner owner, Player player, String channel);

    /** Removes every sidebar channel belonging to this owner for the player. */
    void clear(ServiceOwner owner, Player player);

    /** Removes the owner's sidebar channels for all players. */
    void clearOwner(ServiceOwner owner);
}
