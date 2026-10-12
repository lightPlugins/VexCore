package dev.vexsoft.core.velocity.service.teleport;

import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparationResult;

/** Delivers authenticated source acknowledgements to the proxy's connection gate. */
@Dependencies(ServerTransferService.class)
public final class VexTeleportPreparationResultHandler implements MessageHandler<TeleportPreparationResult> {

    private final ServerTransferService transfers;

    public VexTeleportPreparationResultHandler(final VexServiceRegistry services) {
        transfers = services.require(ServerTransferService.class);
    }

    @Override
    public MessageType<TeleportPreparationResult> getMessageType() {
        return TeleportMessages.PREPARATION_RESULT;
    }

    @Override
    public void handle(final TeleportPreparationResult result, final MessageContext context) {
        transfers.prepared(result, context.getSourceServer());
    }
}
