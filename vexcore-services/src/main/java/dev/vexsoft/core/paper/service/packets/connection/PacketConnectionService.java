package dev.vexsoft.core.paper.service.packets.connection;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;

/**
 * Owns the shared packet pipeline handler installed for every player
 */
public interface PacketConnectionService extends VexService {

  /** Installs the VexCore packet handler for one player */
  void inject(Player player);

  /** Removes the VexCore packet handler from one player */
  void uninject(Player player);
}
