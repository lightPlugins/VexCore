package dev.vexsoft.core.paper.packets.internal;

import java.util.List;

/** Ordered transport writes when rewriting one packet would exceed a protocol bundle limit. */
public record OutboundPacketBatch(List<Object> packets) {
    public OutboundPacketBatch {
        packets = List.copyOf(packets);
    }
}
