package dev.vexsoft.core.paper.service.directory.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import dev.vexsoft.core.paper.service.directory.PlayerDirectoryCoordinatorService;
import java.util.Objects;

/** Delivers Velocity player-directory responses to their pending futures. */
@Dependencies(PlayerDirectoryCoordinatorService.class)
public final class VexPlayerDirectoryResponseHandler implements
    MessageHandler<PlayerDirectoryResponse> {

  private final PlayerDirectoryCoordinatorService directory;

  public VexPlayerDirectoryResponseHandler(final VexServiceRegistry services) {
    directory = Objects.requireNonNull(services, "services")
        .require(PlayerDirectoryCoordinatorService.class);
  }

  @Override
  public MessageType<PlayerDirectoryResponse> getMessageType() {
    return PlayerDirectoryMessages.RESPONSE;
  }

  @Override
  public void handle(final PlayerDirectoryResponse message, final MessageContext context) {
    Objects.requireNonNull(context, "context");
    directory.complete(message);
  }
}
