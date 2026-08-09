package dev.vexsoft.core.paper.packets.internal;

import dev.vexsoft.core.paper.packets.hologram.HologramInteractionType;
import lombok.Value;
import org.bukkit.inventory.EquipmentSlot;

@Value
public class PacketInteractionInput {
  int entityId;
  HologramInteractionType interactionType;
  EquipmentSlot hand;
}
