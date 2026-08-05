package dev.vexsoft.core.packets.display;

import dev.vexsoft.core.api.service.ServiceOwner;
import java.util.UUID;
import lombok.Value;

/** Immutable identity and ownership token for a viewer-specific fake display entity. */
@Value
public class FakeDisplayHandle {
  ServiceOwner owner;
  UUID viewerId;
  int entityId;
  UUID entityUuid;
  FakeDisplayKind kind;
}
