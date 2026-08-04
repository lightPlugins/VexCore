package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.packets.hologram.HologramInteractionType;
import lombok.Value;
import org.bukkit.inventory.EquipmentSlot;

@Value
public class PacketInteractionInput {
  int entityId;
  HologramInteractionType interactionType;
  EquipmentSlot hand;
}
