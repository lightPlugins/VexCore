package dev.vexsoft.core.packets.v26_2.item;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.packets.internal.FakeItemMetaLookup;
import dev.vexsoft.core.packets.internal.ItemMetaPacketAdapterService;
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
