package dev.vexsoft.core.paper.packets.interaction;

import lombok.Value;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Describes one viewer interaction delivered by a virtual interaction entity. */
@Value
public class FakeInteraction {
  Player player;
  FakeInteractionHandle handle;
  FakeInteractionType interactionType;
  EquipmentSlot hand;
}
