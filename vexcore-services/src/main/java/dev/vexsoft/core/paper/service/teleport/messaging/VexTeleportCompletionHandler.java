package dev.vexsoft.core.paper.service.teleport.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.paper.service.teleport.TeleportCoordinatorService;
import java.util.Objects;

/** Completes the source server's pending cross-server teleport future. */
@Dependencies(TeleportCoordinatorService.class)
public final class VexTeleportCompletionHandler implements MessageHandler<TeleportCompletion> {

  private final TeleportCoordinatorService teleports;

  public VexTeleportCompletionHandler(final VexServiceRegistry services) {
    teleports = Objects.requireNonNull(services, "services")
        .require(TeleportCoordinatorService.class);
  }

  @Override
  public MessageType<TeleportCompletion> getMessageType() {
    return TeleportMessages.COMPLETION;
  }

  @Override
  public void handle(final TeleportCompletion message, final MessageContext context) {
    Objects.requireNonNull(context, "context");
    teleports.complete(message);
  }
}
