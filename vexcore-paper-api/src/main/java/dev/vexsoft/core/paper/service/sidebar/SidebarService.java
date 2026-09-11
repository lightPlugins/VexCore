package dev.vexsoft.core.paper.service.sidebar;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/** Owner-scoped persistent and temporary player sidebar channels. */
public interface SidebarService extends VexService {

    /** Sets a persistent channel with the default priority. */
    default void setPersistent(final Player player, final String channel, final SidebarFrame frame) {
        setPersistent(player, channel, frame, 0);
    }

    /** Sets or replaces one persistent owner channel. */
    void setPersistent(Player player, String channel, SidebarFrame frame, int priority);

    /** Opens an independently closable trigger-bound sidebar generation. */
    SidebarHandle open(Player player, String channel, SidebarFrame frame, int priority);

    /** Shows a temporary channel with the default priority. */
    default void showTemporary(
        final Player player,
        final String channel,
        final SidebarFrame frame,
        final long durationTicks
    ) {
        showTemporary(player, channel, frame, durationTicks, 0);
    }

    /** Shows or replaces one temporary owner channel. */
    void showTemporary(Player player, String channel, SidebarFrame frame, long durationTicks, int priority);

    /** Removes one persistent owner channel. */
    void clearPersistent(Player player, String channel);

    /** Removes one temporary owner channel. */
    void clearTemporary(Player player, String channel);

    /** Removes every sidebar channel owned by this service scope for the player. */
    void clear(Player player);
}
