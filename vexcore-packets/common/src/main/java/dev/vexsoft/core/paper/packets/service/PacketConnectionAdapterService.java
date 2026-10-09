package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import org.bukkit.entity.Player;
import java.util.Set;

/**
 * Installs and removes the central VexCore handler in player network channels
 */
public interface PacketConnectionAdapterService extends VexService {

    /** Installs the central packet handler into one player's channel */
    void inject(Player player, PacketDuplexHandler handler);

    /** Removes the central packet handler from one player's channel */
    void uninject(Player player);

    /** Returns a sent-chunk snapshot on the server thread, empty until the chunk loader is ready. */
    Set<Long> getSentChunkKeys(Player player);

    /** Checks a sent chunk on the server thread, returning false before chunk-loader initialization. */
    boolean isChunkSent(Player player, long key);
}
