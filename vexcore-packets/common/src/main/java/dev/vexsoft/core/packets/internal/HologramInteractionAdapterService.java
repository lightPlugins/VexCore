package dev.vexsoft.core.packets.internal;

import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;

/**
 * Decodes version-specific entity interaction packets
 */
public interface HologramInteractionAdapterService extends VexService {

  /** Decodes a native packet when it represents an entity interaction */
  Optional<PacketInteractionInput> decode(Object packet);
}
