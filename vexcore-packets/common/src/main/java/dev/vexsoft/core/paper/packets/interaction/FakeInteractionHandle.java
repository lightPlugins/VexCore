package dev.vexsoft.core.paper.packets.interaction;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.UUID;
import lombok.Value;

/** Immutable identity and ownership token for a viewer-specific interaction entity. */
@Value
public class FakeInteractionHandle {
  ServiceOwner owner;
  UUID viewerId;
  int entityId;
  UUID entityUuid;
}
