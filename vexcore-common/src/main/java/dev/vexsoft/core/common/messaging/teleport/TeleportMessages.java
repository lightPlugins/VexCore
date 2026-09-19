package dev.vexsoft.core.common.messaging.teleport;

import dev.vexsoft.core.api.messaging.MessageKey;
import dev.vexsoft.core.api.messaging.MessageType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Defines the typed network messages used for cross-server teleports. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TeleportMessages {

    public static final MessageType<TeleportTransferRequest> TRANSFER_REQUEST =
        MessageType.json(MessageKey.of("vexcore", "teleport.transfer_request"), TeleportTransferRequest.class);
    public static final MessageType<TeleportArrival> ARRIVAL =
        MessageType.json(MessageKey.of("vexcore", "teleport.arrival"), TeleportArrival.class);
    public static final MessageType<TeleportCompletion> COMPLETION =
        MessageType.json(MessageKey.of("vexcore", "teleport.completion"), TeleportCompletion.class);
}
