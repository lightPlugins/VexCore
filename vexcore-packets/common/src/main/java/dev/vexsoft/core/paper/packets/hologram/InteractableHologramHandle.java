package dev.vexsoft.core.paper.packets.hologram;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.UUID;
import lombok.Value;

/** Immutable ownership token for the paired text-display and interaction entities of a hologram. */
@Value
public class InteractableHologramHandle {
  ServiceOwner owner;
  UUID viewerId;
  int textDisplayEntityId;
  UUID textDisplayEntityUuid;
  int interactionEntityId;
  UUID interactionEntityUuid;
}
