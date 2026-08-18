package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Plays player animations for individual viewers without broadcasting them. */
public interface PlayerAnimationPacketService extends VexService {

  /**
   * Shows a main-hand swing by the target player to the selected viewer.
   *
   * @param viewer player receiving the animation packet
   * @param target player whose main hand should appear to swing
   */
  default void swingMainHand(final Player viewer, final Player target) {
    swingHand(viewer, target, EquipmentSlot.HAND);
  }

  /**
   * Shows an off-hand swing by the target player to the selected viewer.
   *
   * @param viewer player receiving the animation packet
   * @param target player whose off hand should appear to swing
   */
  default void swingOffHand(final Player viewer, final Player target) {
    swingHand(viewer, target, EquipmentSlot.OFF_HAND);
  }

  /**
   * Shows one hand swing by the target player to the selected viewer.
   *
   * @param viewer player receiving the animation packet
   * @param target player whose hand should appear to swing
   * @param hand {@link EquipmentSlot#HAND} or {@link EquipmentSlot#OFF_HAND}
   */
  void swingHand(Player viewer, Player target, EquipmentSlot hand);
}
