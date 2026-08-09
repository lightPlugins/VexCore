package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Plays entity hit animations for individual viewers
 */
public interface MobHitPacketService extends VexService {

  /** Shows the target's hit animation to the given viewer */
  void playHit(Player viewer, LivingEntity target);
}
