package dev.vexsoft.core.api.messaging;

import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Describes the proxy, server, or player that should receive a message */
@Getter
@EqualsAndHashCode
@ToString
public final class MessageTarget {

  private final MessageTargetType type;
  private final String value;

  private MessageTarget(final MessageTargetType type, final String value) {
    this.type = Objects.requireNonNull(type, "type");
    this.value = Objects.requireNonNull(value, "value");
  }

  /** Targets services running directly on the Velocity proxy */
  public static MessageTarget proxy() {
    return new MessageTarget(MessageTargetType.PROXY, "");
  }

  /** Targets one backend server by its Velocity registration name */
  public static MessageTarget server(final String serverName) {
    String checkedName = Objects.requireNonNull(serverName, "serverName").trim();
    if (checkedName.isEmpty()) {
      throw new IllegalArgumentException("Server name cannot be empty");
    }
    return new MessageTarget(MessageTargetType.SERVER, checkedName);
  }

  /** Targets the backend server currently hosting the specified player */
  public static MessageTarget player(final UUID playerId) {
    return new MessageTarget(
        MessageTargetType.PLAYER,
        Objects.requireNonNull(playerId, "playerId").toString()
    );
  }

  /** Targets every backend server except the server that sent the message */
  public static MessageTarget allServers() {
    return new MessageTarget(MessageTargetType.ALL_SERVERS, "");
  }

  /** Recreates a target read from the messaging protocol */
  public static MessageTarget decoded(final MessageTargetType type, final String value) {
    return switch (Objects.requireNonNull(type, "type")) {
      case PROXY -> proxy();
      case SERVER -> server(value);
      case PLAYER -> player(UUID.fromString(value));
      case ALL_SERVERS -> allServers();
    };
  }
}
