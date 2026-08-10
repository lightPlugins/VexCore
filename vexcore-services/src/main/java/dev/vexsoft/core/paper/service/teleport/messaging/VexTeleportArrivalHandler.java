package dev.vexsoft.core.paper.service.teleport.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportResult;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.common.messaging.teleport.TeleportArrival;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.paper.service.teleport.TeleportCoordinatorService;
import java.util.Objects;

/** Completes a transferred player's asynchronous teleport on the destination server. */
@Dependencies({TeleportCoordinatorService.class, MessagingService.class})
public final class VexTeleportArrivalHandler implements MessageHandler<TeleportArrival> {

  private final TeleportCoordinatorService teleports;
  private final MessagingService messages;

  public VexTeleportArrivalHandler(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    teleports = checkedServices.require(TeleportCoordinatorService.class);
    messages = checkedServices.require(MessagingService.class);
  }

  @Override
  public MessageType<TeleportArrival> getMessageType() {
    return TeleportMessages.ARRIVAL;
  }

  @Override
  public void handle(final TeleportArrival message, final MessageContext context) {
    Objects.requireNonNull(context, "context");
    teleports.acceptArrival(message.playerId(), message.destination())
        .whenComplete((result, throwable) -> sendCompletion(message, result, throwable));
  }

  private void sendCompletion(
      final TeleportArrival arrival,
      final TeleportResult result,
      final Throwable throwable
  ) {
    TeleportCompletion completion = throwable == null
        ? new TeleportCompletion(arrival.requestId(), result.status(), result.message())
        : new TeleportCompletion(
            arrival.requestId(),
            TeleportStatus.FAILED,
            "The destination server could not finish the teleport"
        );
    messages.send(
        MessageTarget.server(arrival.sourceServer()),
        TeleportMessages.COMPLETION,
        completion
    );
  }
}
