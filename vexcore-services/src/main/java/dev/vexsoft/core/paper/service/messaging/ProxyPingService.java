package dev.vexsoft.core.paper.service.messaging;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Tracks pending proxy pings and reports their localized result to players */
public interface ProxyPingService extends VexService {

  /** Starts a pending ping and returns its unique request identifier */
  UUID begin(Player player);

  /** Cancels a pending ping when its request could not be sent */
  void cancel(UUID requestId);

  /** Completes a pending ping after Velocity returns its response */
  void complete(UUID requestId);
}
