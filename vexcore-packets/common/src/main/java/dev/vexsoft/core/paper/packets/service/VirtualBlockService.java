package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.block.VirtualBlock;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Projects shared or personal blocks and supplies thread-safe snapshots to packet adapters.
 * Publish, remove and synchronize on the server thread; connection bookkeeping and snapshot reads are network-safe.
 */
public interface VirtualBlockService extends VexService {
    /** Publishes and immediately sends a projection; a null audience means every viewer of the world. */
    void put(String world, UUID audience, VirtualBlock block);

    /** Removes a projection and immediately reveals the remaining projection or original block. */
    void remove(String world, UUID audience, int x, int y, int z);

    /** Binds a connection to its world without accessing Bukkit from the network thread. */
    void bind(UUID viewer, String world);

    /** Returns the current projection for a viewer, or null when the position is real. */
    VirtualBlock find(UUID viewer, int x, int y, int z);

    /** Returns only the projections inside one outgoing chunk. */
    List<VirtualBlock> chunk(UUID viewer, int x, int z);

    /** Records a predicted action so its virtual state can be sent before the matching acknowledgement. */
    void predict(UUID viewer, int sequence, int x, int y, int z);

    /** Consumes predicted positions through a sequence and returns their current projections. */
    List<VirtualBlock> acknowledge(UUID viewer, int sequence);

    /** Drops connection bookkeeping without resetting personal resource cooldowns. */
    void disconnect(UUID viewer);

    /** Binds and synchronizes projections in the chunks already sent to an online player. */
    void synchronize(Player viewer);
}
