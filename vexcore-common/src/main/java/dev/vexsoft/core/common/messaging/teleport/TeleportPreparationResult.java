package dev.vexsoft.core.common.messaging.teleport;

import java.util.UUID;

/** Confirms that the source has saved and published the matching durable handover. */
public record TeleportPreparationResult(UUID requestId, boolean successful) {
}
