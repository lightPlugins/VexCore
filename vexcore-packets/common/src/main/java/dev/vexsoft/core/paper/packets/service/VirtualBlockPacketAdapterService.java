package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.UUID;

/** Preserves virtual blocks and client prediction using the selected native protocol. */
public interface VirtualBlockPacketAdapterService extends VexService {
    /** Rewrites block updates and restores predicted virtual states before acknowledgements. */
    Object rewrite(UUID viewer, Object packet);

    /** Tracks prediction sequences while retaining normal server-side interaction validation. */
    void predict(UUID viewer, Object packet);
}
