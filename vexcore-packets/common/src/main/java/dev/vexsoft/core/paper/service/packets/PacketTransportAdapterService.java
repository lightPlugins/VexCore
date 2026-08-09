package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Sends version-specific packet objects to connected players
 */
public interface PacketTransportAdapterService extends VexService {

  /** Sends one native packet object to the given player */
  void send(Player player, Object packet);

  /** Sends native packet objects as one bundle when supported */
  void sendBundle(Player player, List<Object> packets);
}
