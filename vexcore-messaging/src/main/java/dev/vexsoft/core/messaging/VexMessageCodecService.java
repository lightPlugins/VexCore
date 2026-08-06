package dev.vexsoft.core.messaging;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageTargetType;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.Objects;
import java.util.UUID;

@Dependencies
public final class VexMessageCodecService implements MessageCodecService {

  public static final int MAX_MESSAGE_BYTES = 32_768;

  private static final int MAGIC = 0x5645584D;
  private static final int PROTOCOL_VERSION = 1;

  public VexMessageCodecService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public byte[] encode(final MessageEnvelope message) {
    Objects.requireNonNull(message, "message");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
        output.writeInt(MAGIC);
        output.writeInt(PROTOCOL_VERSION);
        output.writeLong(message.getMessageId().getMostSignificantBits());
        output.writeLong(message.getMessageId().getLeastSignificantBits());
        output.writeUTF(message.getMessageKey().getNamespace());
        output.writeUTF(message.getMessageKey().getValue());
        output.writeByte(message.getTarget().getType().ordinal());
        output.writeUTF(message.getTarget().getValue());
        output.writeUTF(message.getSourceOwner());
        output.writeUTF(message.getSourceServer());
        output.writeLong(message.getCreatedAt());
        output.writeLong(message.getTimeToLiveMillis());
        byte[] payload = message.getPayload();
        output.writeInt(payload.length);
        output.write(payload);
      }
      byte[] encoded = bytes.toByteArray();
      if (encoded.length > MAX_MESSAGE_BYTES) {
        throw new IllegalArgumentException("Message exceeds " + MAX_MESSAGE_BYTES + " bytes");
      }
      return encoded;
    } catch (UTFDataFormatException exception) {
      throw new IllegalArgumentException("Message contains a value that is too long", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to encode network message", exception);
    }
  }

  @Override
  public MessageEnvelope decode(final byte[] data) {
    byte[] checkedData = Objects.requireNonNull(data, "data");
    if (checkedData.length == 0 || checkedData.length > MAX_MESSAGE_BYTES) {
      throw new IllegalArgumentException("Invalid network message size: " + checkedData.length);
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(checkedData))) {
      if (input.readInt() != MAGIC) {
        throw new IllegalArgumentException("Invalid VexCore message header");
      }
      int version = input.readInt();
      if (version != PROTOCOL_VERSION) {
        throw new IllegalArgumentException("Unsupported messaging protocol version: " + version);
      }
      UUID messageId = new UUID(input.readLong(), input.readLong());
      MessageKey key = MessageKey.of(input.readUTF(), input.readUTF());
      MessageTarget target = MessageTarget.decoded(
          targetType(input.readUnsignedByte()),
          input.readUTF()
      );
      String sourceOwner = input.readUTF();
      String sourceServer = input.readUTF();
      long createdAt = input.readLong();
      long timeToLive = input.readLong();
      int payloadSize = input.readInt();
      if (payloadSize < 0 || payloadSize > input.available()) {
        throw new IllegalArgumentException("Invalid message payload size: " + payloadSize);
      }
      byte[] payload = input.readNBytes(payloadSize);
      if (input.available() != 0) {
        throw new IllegalArgumentException("Network message contains trailing data");
      }
      return new MessageEnvelope(
          messageId,
          key,
          target,
          sourceOwner,
          sourceServer,
          createdAt,
          timeToLive,
          payload
      );
    } catch (EOFException exception) {
      throw new IllegalArgumentException("Network message is incomplete", exception);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to decode network message", exception);
    }
  }

  private MessageTargetType targetType(final int ordinal) {
    MessageTargetType[] values = MessageTargetType.values();
    if (ordinal >= values.length) {
      throw new IllegalArgumentException("Unknown message target type: " + ordinal);
    }
    return values[ordinal];
  }
}
