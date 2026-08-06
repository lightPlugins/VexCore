package dev.vexsoft.core.api.messaging;

import java.util.UUID;
import lombok.Value;

/** Supplies delivery metadata to a registered message handler */
@Value
public class MessageContext {
  UUID messageId;
  String sourceOwner;
  String sourceServer;
  long createdAt;
}
