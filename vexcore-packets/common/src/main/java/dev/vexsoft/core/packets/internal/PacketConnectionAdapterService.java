package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.api.service.VexService;
import org.bukkit.entity.Player;

/**
 * Installs and removes the central VexCore handler in player network channels
 */
public interface PacketConnectionAdapterService extends VexService {

  /** Installs the central packet handler into one player's channel */
  public void inject(Player player, PacketDuplexHandler handler);

  /** Removes the central packet handler from one player's channel */
  public void uninject(Player player);
}
