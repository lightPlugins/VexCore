package dev.vexsoft.core.common.messaging.directory;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageType;

/** Defines typed request and response messages for the network player directory. */
public final class PlayerDirectoryMessages {

  public static final MessageType<PlayerDirectoryRequest> REQUEST = MessageType.json(
      MessageKey.of("vexcore", "directory.player_request"),
      PlayerDirectoryRequest.class
  );
  public static final MessageType<PlayerDirectoryResponse> RESPONSE = MessageType.json(
      MessageKey.of("vexcore", "directory.player_response"),
      PlayerDirectoryResponse.class
  );

  private PlayerDirectoryMessages() { }
}
