package dev.vexsoft.core.api.messaging;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Associates a stable message key with its JSON payload type */
@Getter
@EqualsAndHashCode
@ToString
public final class MessageType<T> {

  private final MessageKey key;
  private final Class<T> payloadType;

  private MessageType(final MessageKey key, final Class<T> payloadType) {
    this.key = Objects.requireNonNull(key, "key");
    this.payloadType = Objects.requireNonNull(payloadType, "payloadType");
  }

  /** Creates a message type whose payload is encoded as JSON */
  public static <T> MessageType<T> json(
      final MessageKey key,
      final Class<T> payloadType
  ) {
    return new MessageType<>(key, payloadType);
  }
}
