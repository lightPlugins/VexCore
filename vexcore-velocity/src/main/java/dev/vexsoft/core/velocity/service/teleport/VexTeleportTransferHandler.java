package dev.vexsoft.core.velocity.service.teleport;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;

/** Routes explicit player teleports through the same handover gate as ordinary proxy switches. */
@Dependencies(ServerTransferService.class)
public final class VexTeleportTransferHandler implements MessageHandler<TeleportTransferRequest> {

    private final ServerTransferService transfers;

    public VexTeleportTransferHandler(final VexServiceRegistry services) {
        transfers = services.require(ServerTransferService.class);
    }

    @Override
    public MessageType<TeleportTransferRequest> getMessageType() {
        return TeleportMessages.TRANSFER_REQUEST;
    }

    @Override
    public void handle(final TeleportTransferRequest request, final MessageContext context) {
        transfers.transfer(request, context.getSourceServer());
    }
}
