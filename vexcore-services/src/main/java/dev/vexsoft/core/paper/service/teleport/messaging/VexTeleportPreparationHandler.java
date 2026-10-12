package dev.vexsoft.core.paper.service.teleport.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparationResult;
import dev.vexsoft.core.paper.service.teleport.TeleportCoordinatorService;

/** Saves the authenticated proxy's source session before acknowledging a connection gate. */
@Dependencies({TeleportCoordinatorService.class, MessagingService.class})
public final class VexTeleportPreparationHandler implements MessageHandler<TeleportPreparation> {

    private final TeleportCoordinatorService teleports;
    private final MessagingService messages;

    public VexTeleportPreparationHandler(final VexServiceRegistry services) {
        teleports = services.require(TeleportCoordinatorService.class);
        messages = services.require(MessagingService.class);
    }

    @Override
    public MessageType<TeleportPreparation> getMessageType() {
        return TeleportMessages.PREPARATION;
    }

    @Override
    public void handle(final TeleportPreparation request, final MessageContext context) {
        if (!context.getSourceServer().isBlank()) {
            return;
        }
        teleports.prepare(request).whenComplete((prepared, failure) -> messages.send(MessageTarget.proxy(),
            TeleportMessages.PREPARATION_RESULT,
            new TeleportPreparationResult(request.requestId(), failure == null && Boolean.TRUE.equals(prepared))));
    }
}
