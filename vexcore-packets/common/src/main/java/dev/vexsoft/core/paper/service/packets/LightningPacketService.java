package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Shows client-side lightning strikes to individual viewers
 */
public interface LightningPacketService extends VexService {

  /** Shows a lightning strike at the target to the given viewer */
  void strike(Player viewer, LivingEntity target);
}
