package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/**
 * Installs and removes the central VexCore handler in player network channels
 */
public interface PacketConnectionAdapterService extends VexService {

  /** Installs the central packet handler into one player's channel */
  void inject(Player player, PacketDuplexHandler handler);

  /** Removes the central packet handler from one player's channel */
  void uninject(Player player);
}
