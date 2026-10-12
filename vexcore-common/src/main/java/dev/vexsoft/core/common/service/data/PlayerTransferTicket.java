package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable, single-use handover of the source's final selected profiles and optional explicit destination. */
public record PlayerTransferTicket(
    UUID requestId, UUID playerId, String sourceServer, String targetServer, ServerPosition destination,
    List<PlayerProfileContext> profiles, long expiresAt, UUID claimant, boolean finished, TransferOptions options
) {

    public PlayerTransferTicket(
        final UUID requestId, final UUID playerId, final String sourceServer, final String targetServer,
        final ServerPosition destination, final List<PlayerProfileContext> profiles, final long expiresAt,
        final UUID claimant, final boolean finished
    ) {
        this(requestId, playerId, sourceServer, targetServer, destination, profiles, expiresAt,
            claimant, finished, TransferOptions.ordinary());
    }

    public PlayerTransferTicket {
        options = options == null ? TransferOptions.ordinary() : options;
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        if (sourceServer == null || sourceServer.isBlank() || targetServer == null || targetServer.isBlank()
            || sourceServer.equals(targetServer) || expiresAt <= 0) {
            throw new IllegalArgumentException("Invalid server handover");
        }
        profiles = List.copyOf(profiles);
        if (profiles.stream().anyMatch(profile -> !profile.playerId().equals(playerId))
            || destination != null && !destination.server().value().equals(targetServer)) {
            throw new IllegalArgumentException("Handover identity or destination mismatch");
        }
    }

    /** Binds this handover to one destination login without changing its captured selection. */
    public PlayerTransferTicket claimed(final UUID login) {
        return new PlayerTransferTicket(requestId, playerId, sourceServer, targetServer, destination,
            profiles, expiresAt, Objects.requireNonNull(login, "login"), false, options);
    }

    /** Retains a compact replay tombstone after completion or cancellation. */
    public PlayerTransferTicket completed() {
        return new PlayerTransferTicket(requestId, playerId, sourceServer, targetServer, destination,
            profiles, expiresAt, claimant, true, options);
    }
}
