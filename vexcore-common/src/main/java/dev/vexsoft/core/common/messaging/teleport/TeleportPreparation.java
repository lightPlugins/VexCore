package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.UUID;

/** Requests source retirement before Velocity permits an optionally admitted backend switch. */
public record TeleportPreparation(
    UUID requestId, UUID playerId, String sourceServer, String targetServer, ServerPosition destination,
    TransferOptions options
) {

    public TeleportPreparation(final UUID requestId, final UUID playerId, final String sourceServer,
        final String targetServer, final ServerPosition destination) {
        this(requestId, playerId, sourceServer, targetServer, destination, TransferOptions.ordinary());
    }

    public TeleportPreparation {
        options = options == null ? TransferOptions.ordinary() : options;
    }
}
