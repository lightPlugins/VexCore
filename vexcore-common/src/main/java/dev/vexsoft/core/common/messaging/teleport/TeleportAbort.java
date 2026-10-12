package dev.vexsoft.core.common.messaging.teleport;

import java.util.UUID;

/** Cancels an unclaimed handover after a rejected connection or timeout. */
public record TeleportAbort(UUID requestId, UUID playerId) {
}
