package dev.vexsoft.core.paper.packets.v26_2.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;

/** Retains client click prediction while the server commits a menu's asynchronous item exchange. */
public final class V26_2InventorySynchronization {

    private final Map<UUID, Hold> held = new ConcurrentHashMap<>();

    /** Starts one menu hold and returns an idempotent settlement action fenced against replacement menus. */
    public Runnable hold(final UUID viewerId, final int containerId, final Runnable synchronize) {
        var ticket = new Hold(containerId);
        if (held.putIfAbsent(viewerId, ticket) != null) {
            throw new IllegalStateException("Inventory synchronization is already deferred");
        }
        return () -> {
            if (held.remove(viewerId, ticket)) {
                synchronize.run();
            }
        };
    }

    /** Filters only item corrections; opening or closing a screen retires the old hold immediately. */
    public Object filter(final UUID viewerId, final Object packet) {
        if (packet instanceof ClientboundOpenScreenPacket || packet instanceof ClientboundContainerClosePacket) {
            clear(viewerId);
            return packet;
        }
        var hold = held.get(viewerId);
        if (hold == null) {
            return packet;
        }
        if (packet instanceof ClientboundSetCursorItemPacket || packet instanceof ClientboundSetPlayerInventoryPacket
            || packet instanceof ClientboundContainerSetContentPacket content && content.containerId() == hold.containerId
            || packet instanceof ClientboundContainerSetSlotPacket slot && slot.getContainerId() == hold.containerId) {
            return null;
        }
        return packet;
    }

    /** Clears a closed or disconnected menu without replaying its settlement action. */
    public void clear(final UUID viewerId) {
        held.remove(viewerId);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Hold {

        private final int containerId;
    }
}
