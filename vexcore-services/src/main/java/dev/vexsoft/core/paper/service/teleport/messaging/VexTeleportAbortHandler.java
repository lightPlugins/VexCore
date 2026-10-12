package dev.vexsoft.core.paper.service.teleport.messaging;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.paper.service.teleport.TeleportCoordinatorService;

/** Restores only the source of an unclaimed, cancelled proxy handover. */
@Dependencies(TeleportCoordinatorService.class)
public final class VexTeleportAbortHandler implements MessageHandler<TeleportAbort> {

    private final TeleportCoordinatorService teleports;

    public VexTeleportAbortHandler(final VexServiceRegistry services) {
        teleports = services.require(TeleportCoordinatorService.class);
    }

    @Override
    public MessageType<TeleportAbort> getMessageType() {
        return TeleportMessages.ABORT;
    }

    @Override
    public void handle(final TeleportAbort request, final MessageContext context) {
        if (context.getSourceServer().isBlank()) {
            teleports.abort(request);
        }
    }
}
