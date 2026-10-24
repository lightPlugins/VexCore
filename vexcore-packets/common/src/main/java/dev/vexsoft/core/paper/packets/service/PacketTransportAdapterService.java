package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Sends version-specific packet objects to connected players
 */
public interface PacketTransportAdapterService extends VexService {

    /** Sends one native packet object to the given player */
    void send(Player player, Object packet);

    /** Sends native packet objects as one bundle when supported */
    void sendBundle(Player player, List<Object> packets);

    /** Holds inventory corrections during one asynchronous exchange; run the result on the player thread to resync. */
    Runnable deferInventorySynchronization(Player player);

    /** Drops corrections for a held menu, leaving the client's predicted click visible until settlement. */
    Object filterInventorySynchronization(UUID viewerId, Object packet);

    /** Removes a disconnected viewer's held menu without sending packets. */
    void clearInventorySynchronization(UUID viewerId);
}
