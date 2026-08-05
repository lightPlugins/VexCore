package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.display.DisplayGlowColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
}
