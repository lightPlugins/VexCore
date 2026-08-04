package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.api.service.VexService;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Sends version-specific packet objects to connected players
 */
public interface PacketTransportAdapterService extends VexService {

  /** Sends one native packet object to the given player */
  public void send(Player player, Object packet);

  /** Sends native packet objects as one bundle when supported */
  public void sendBundle(Player player, List<Object> packets);
}
