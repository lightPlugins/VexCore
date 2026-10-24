package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import dev.vexsoft.core.paper.packets.v26_2.item.V26_2InventorySynchronization;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Sends individual packets and packet bundles to Minecraft 26.2 players. */
@Dependencies
public final class VexPacketTransportAdapterService implements PacketTransportAdapterService {

    private final V26_2InventorySynchronization inventories = new V26_2InventorySynchronization();

    public VexPacketTransportAdapterService(final VexServiceRegistry services) {
    }

    @Override
    public Runnable deferInventorySynchronization(final Player player) {
        var handle = ((CraftPlayer) player).getHandle();
        int containerId = handle.containerMenu.containerId;
        return inventories.hold(player.getUniqueId(), containerId, () -> {
            if (player.isOnline() && Bukkit.isOwnedByCurrentRegion(player)
                && handle.containerMenu.containerId == containerId) {
                player.updateInventory();
            }
        });
    }

    @Override
    public Object filterInventorySynchronization(final UUID viewerId, final Object packet) {
        return inventories.filter(viewerId, packet);
    }

    @Override
    public void clearInventorySynchronization(final UUID viewerId) {
        inventories.clear(viewerId);
    }

    @Override
    public void send(final Player player, final Object packet) {
        ((CraftPlayer) player).getHandle().connection.send(requirePacket(packet));
    }

    @Override
    public void sendBundle(final Player player, final List<Object> packets) {
        if (packets.isEmpty()) {
            return;
        }

        if (packets.size() == 1) {
            send(player, packets.getFirst());

            return;
        }

        List<Packet<? super ClientGamePacketListener>> checkedPackets = new ArrayList<>(packets.size());

        for (Object packet : packets) {
            checkedPackets.add(requireGamePacket(packet));
        }

        send(player, new ClientboundBundlePacket(checkedPackets));
    }

    private static Packet<?> requirePacket(final Object packet) {
        if (!(packet instanceof Packet<?> checkedPacket)) {
            throw new IllegalArgumentException("Packet adapter received a non-packet value");
        }

        return checkedPacket;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientGamePacketListener> requireGamePacket(final Object packet) {
        return (Packet<? super ClientGamePacketListener>) requirePacket(packet);
    }
}
