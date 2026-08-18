package dev.vexsoft.core.paper.packets.internal;

import dev.vexsoft.core.paper.packets.interaction.FakeInteractionType;
import lombok.Value;
import org.bukkit.inventory.EquipmentSlot;

@Value
public class PacketInteractionInput {
  int entityId;
  FakeInteractionType interactionType;
  EquipmentSlot hand;
}
