package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.hologram.HologramInteractionType;
import dev.vexsoft.core.paper.packets.service.HologramInteractionAdapterService;
import dev.vexsoft.core.paper.packets.internal.PacketInteractionInput;
import java.util.Optional;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import org.bukkit.inventory.EquipmentSlot;

@Dependencies
public final class VexHologramInteractionAdapterService
    implements HologramInteractionAdapterService {

  public VexHologramInteractionAdapterService(final VexServiceRegistry services) {
  }

  @Override
  public Optional<PacketInteractionInput> decode(final Object packet) {
    if (packet instanceof ServerboundAttackPacket attackPacket) {
      return Optional.of(new PacketInteractionInput(
          attackPacket.entityId(),
          HologramInteractionType.LEFT_CLICK,
          EquipmentSlot.HAND
      ));
    }
    if (packet instanceof ServerboundInteractPacket interactPacket) {
      EquipmentSlot hand = interactPacket.hand() == InteractionHand.OFF_HAND
          ? EquipmentSlot.OFF_HAND
          : EquipmentSlot.HAND;
      return Optional.of(new PacketInteractionInput(
          interactPacket.entityId(),
          HologramInteractionType.RIGHT_CLICK,
          hand
      ));
    }
    return Optional.empty();
  }
}
