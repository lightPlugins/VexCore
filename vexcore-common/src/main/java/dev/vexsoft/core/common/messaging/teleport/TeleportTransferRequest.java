package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.Objects;
import java.util.UUID;

/** Requests an authenticated Velocity transfer with optional fenced admission. */
public record TeleportTransferRequest(
    UUID requestId, UUID playerId, ServerPosition destination, String sourceServer, TransferOptions options
) {

    public TeleportTransferRequest(final UUID requestId, final UUID playerId, final ServerPosition destination,
        final String sourceServer) {
        this(requestId, playerId, destination, sourceServer, TransferOptions.ordinary());
    }

    public TeleportTransferRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sourceServer, "sourceServer");
        options = options == null ? TransferOptions.ordinary() : options;
    }
}
