package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Controls viewer-specific entity glow effects
 */
public interface MobGlowPacketService extends VexService {

  /** Enables the colored glow effect for one viewer */
  void setGlow(Player viewer, LivingEntity target, DisplayGlowColor color);

  /** Removes the viewer-specific glow effect from the target */
  void clearGlow(Player viewer, LivingEntity target);
}
