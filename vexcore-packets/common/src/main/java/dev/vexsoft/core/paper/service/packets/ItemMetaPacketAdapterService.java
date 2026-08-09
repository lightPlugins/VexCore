package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.UUID;

/**
 * Rewrites item metadata inside version-specific inventory packets
 */
public interface ItemMetaPacketAdapterService extends VexService {

  /** Applies fake metadata to supported outbound item packets */
  Object rewriteOutbound(UUID viewerId, Object packet, FakeItemMetaLookup lookup);

  /** Removes fake metadata from supported inbound item packets */
  Object sanitizeInbound(UUID viewerId, Object packet, FakeItemMetaLookup lookup);
}
