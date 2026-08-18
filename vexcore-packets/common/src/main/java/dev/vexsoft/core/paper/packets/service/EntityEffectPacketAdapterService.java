package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Encodes viewer-specific entity effect packets
 */
public interface EntityEffectPacketAdapterService extends VexService {

  /** Shows an entity hit animation to one viewer */
  void playHit(Player viewer, LivingEntity target);

  /** Enables a colored entity glow for one viewer */
  void setGlow(Player viewer, LivingEntity target, DisplayGlowColor color);

  /** Removes a viewer-specific entity glow */
  void clearGlow(Player viewer, LivingEntity target);

  /** Shows a client-side lightning strike at an entity */
  void strikeLightning(Player viewer, LivingEntity target);

  /**
   * Shows one hand-swing animation for a player to the selected viewer.
   *
   * @param viewer player receiving the packet
   * @param target player whose hand should appear to swing
   * @param hand main- or off-hand equipment slot
   */
  void swingHand(Player viewer, Player target, EquipmentSlot hand);
}
