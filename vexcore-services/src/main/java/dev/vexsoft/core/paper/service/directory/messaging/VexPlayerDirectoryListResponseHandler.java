package dev.vexsoft.core.paper.service.directory.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListResponse;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.paper.service.directory.PlayerDirectoryCoordinatorService;
import java.util.Objects;

/** Delivers Velocity network-player snapshots to the Paper directory cache. */
@Dependencies(PlayerDirectoryCoordinatorService.class)
public final class VexPlayerDirectoryListResponseHandler implements
    MessageHandler<PlayerDirectoryListResponse> {

  private final PlayerDirectoryCoordinatorService directory;

  public VexPlayerDirectoryListResponseHandler(final VexServiceRegistry services) {
    directory = Objects.requireNonNull(services, "services")
        .require(PlayerDirectoryCoordinatorService.class);
  }

  @Override
  public MessageType<PlayerDirectoryListResponse> getMessageType() {
    return PlayerDirectoryMessages.LIST_RESPONSE;
  }

  @Override
  public void handle(final PlayerDirectoryListResponse message, final MessageContext context) {
    directory.complete(message);
  }
}
