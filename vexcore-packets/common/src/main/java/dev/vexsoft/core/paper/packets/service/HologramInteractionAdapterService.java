package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.internal.PacketInteractionInput;
import java.util.Optional;

/**
 * Decodes version-specific entity interaction packets
 */
public interface HologramInteractionAdapterService extends VexService {

    /** Decodes a native packet when it represents an entity interaction */
    Optional<PacketInteractionInput> decode(Object packet);
}
