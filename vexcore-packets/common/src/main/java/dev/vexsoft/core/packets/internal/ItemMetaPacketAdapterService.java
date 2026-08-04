package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.api.service.VexService;
import java.util.UUID;

/**
 * Rewrites item metadata inside version-specific inventory packets
 */
public interface ItemMetaPacketAdapterService extends VexService {

  /** Applies fake metadata to supported outbound item packets */
  public Object rewriteOutbound(UUID viewerId, Object packet, FakeItemMetaLookup lookup);

  /** Removes fake metadata from supported inbound item packets */
  public Object sanitizeInbound(UUID viewerId, Object packet, FakeItemMetaLookup lookup);
}
