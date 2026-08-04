package dev.vexsoft.core.packets.display;

import dev.vexsoft.core.api.service.ServiceOwner;
import java.util.UUID;
import lombok.Value;

@Value
public class FakeDisplayHandle {
  ServiceOwner owner;
  UUID viewerId;
  int entityId;
  UUID entityUuid;
  FakeDisplayKind kind;
}
