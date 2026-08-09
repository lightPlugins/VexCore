package dev.vexsoft.core.common.service.messaging;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageTarget;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** Holds the transport metadata and encoded payload of one network message */
@Getter
public final class MessageEnvelope {

  private final UUID messageId;
  private final MessageKey messageKey;
  private final MessageTarget target;
  private final String sourceOwner;
  private final String sourceServer;
  private final long createdAt;
  private final long timeToLiveMillis;
  private final byte[] payload;

  public MessageEnvelope(
      final UUID messageId,
      final MessageKey messageKey,
      final MessageTarget target,
      final String sourceOwner,
      final String sourceServer,
      final long createdAt,
      final long timeToLiveMillis,
      final byte[] payload
  ) {
    this.messageId = Objects.requireNonNull(messageId, "messageId");
    this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
    this.target = Objects.requireNonNull(target, "target");
    this.sourceOwner = Objects.requireNonNull(sourceOwner, "sourceOwner");
    this.sourceServer = Objects.requireNonNull(sourceServer, "sourceServer");
    this.createdAt = createdAt;
    if (timeToLiveMillis <= 0) {
      throw new IllegalArgumentException("Message time to live must be positive");
    }
    this.timeToLiveMillis = timeToLiveMillis;
    this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
  }

  /** Returns a defensive copy of the encoded payload */
  public byte[] getPayload() {
    return Arrays.copyOf(payload, payload.length);
  }

  /** Returns whether this message has exceeded its configured lifetime */
  public boolean isExpired(final long currentTime) {
    return currentTime - createdAt >= timeToLiveMillis;
  }

  /** Replaces the untrusted source with the server name determined by Velocity */
  public MessageEnvelope withSourceServer(final String serverName) {
    return new MessageEnvelope(
        messageId,
        messageKey,
        target,
        sourceOwner,
        serverName,
        createdAt,
        timeToLiveMillis,
        payload
    );
  }
}
