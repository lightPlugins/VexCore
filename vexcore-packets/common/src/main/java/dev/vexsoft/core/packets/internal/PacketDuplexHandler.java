package dev.vexsoft.core.packets.internal;

import java.util.UUID;

/**
 * Processes native inbound and outbound packets without exposing Netty publicly
 */
public interface PacketDuplexHandler {

  /** Rewrites an outbound packet before it reaches the client */
  public Object write(UUID viewerId, Object packet);

  /** Rewrites an inbound packet or returns null to consume it */
  public Object read(UUID viewerId, Object packet);
}
