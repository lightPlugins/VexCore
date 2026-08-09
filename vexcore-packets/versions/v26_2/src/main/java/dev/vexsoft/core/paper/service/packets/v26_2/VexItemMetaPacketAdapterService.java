package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.paper.packets.v26_2.item.V26_2ItemMetaPacketRewriter;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.paper.packets.service.ItemMetaPacketAdapterService;
import java.util.UUID;

@Dependencies
public final class VexItemMetaPacketAdapterService implements ItemMetaPacketAdapterService {

  private final V26_2ItemMetaPacketRewriter rewriter = new V26_2ItemMetaPacketRewriter();

  public VexItemMetaPacketAdapterService(final VexServiceRegistry services) {
  }

  @Override
  public Object rewriteOutbound(
      final UUID viewerId,
      final Object packet,
      final FakeItemMetaLookup lookup
  ) {
    return rewriter.rewrite(viewerId, packet, lookup);
  }

  @Override
  public Object sanitizeInbound(
      final UUID viewerId,
      final Object packet,
      final FakeItemMetaLookup lookup
  ) {
    return rewriter.sanitize(viewerId, packet, lookup);
  }
}
