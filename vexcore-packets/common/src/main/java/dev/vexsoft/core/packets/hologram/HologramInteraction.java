package dev.vexsoft.core.packets.hologram;

import lombok.Value;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

@Value
public class HologramInteraction {
  Player player;
  InteractableHologramHandle hologram;
  HologramInteractionType interactionType;
  EquipmentSlot hand;
}
