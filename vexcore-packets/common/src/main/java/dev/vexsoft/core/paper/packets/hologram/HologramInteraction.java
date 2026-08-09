package dev.vexsoft.core.paper.packets.hologram;

import lombok.Value;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Describes one viewer interaction delivered by an interactable fake hologram. */
@Value
public class HologramInteraction {
  Player player;
  InteractableHologramHandle hologram;
  HologramInteractionType interactionType;
  EquipmentSlot hand;
}
