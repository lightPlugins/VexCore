package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

@Dependencies
public final class VexPacketTransportAdapterService implements PacketTransportAdapterService {

  public VexPacketTransportAdapterService(final VexServiceRegistry services) {
  }

  @Override
  public void send(final Player player, final Object packet) {
    ((CraftPlayer) player).getHandle().connection.send(requirePacket(packet));
  }

  @Override
  public void sendBundle(final Player player, final List<Object> packets) {
    List<Packet<? super ClientGamePacketListener>> checkedPackets = packets.stream()
        .map(VexPacketTransportAdapterService::requireGamePacket)
        .toList();
    if (checkedPackets.size() == 1) {
      send(player, checkedPackets.getFirst());
      return;
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
