package dev.vexsoft.core.common.service.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Stores handovers alongside existing authoritative Core state, never in a second database. */
@Dependencies(ProfileStorageService.class)
public final class VexPlayerTransferStoreService implements PlayerTransferStoreService {

    private final ProfileStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public VexPlayerTransferStoreService(final VexServiceRegistry services) {
        storage = services.require(ProfileStorageService.class);
    }

    @Override
    public CompletableFuture<Void> prepare(final PlayerTransferTicket ticket) {
        return storage.databaseTime().thenCompose(now -> load(ticket.playerId()).thenCompose(stored -> {
            PlayerTransferTicket previous = decode(stored);
            if (ticket.expiresAt() <= now || ticket.claimant() != null || ticket.finished()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Server handover expired"));
            }
            if (previous != null && previous.requestId().equals(ticket.requestId())) {
                return previous.equals(ticket) ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(new IllegalStateException("Server handover already consumed"));
            }
            if (previous != null && !previous.finished() && previous.expiresAt() > now) {
                return CompletableFuture.failedFuture(new IllegalStateException("Another server handover is pending"));
            }
            return write(ticket, stored, ticket.expiresAt());
        }));
    }

    @Override
    public CompletableFuture<Optional<PlayerTransferTicket>> claim(
        final UUID playerId, final String server, final UUID login
    ) {
        return storage.databaseTime().thenCompose(now -> load(playerId).thenCompose(stored -> {
            PlayerTransferTicket ticket = decode(stored);
            if (ticket != null && ticket.finished() && ticket.claimant() == null && ticket.expiresAt() > now
                && ticket.targetServer().equals(server)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Cancelled server handover arrived late"));
            }
            if (ticket == null || ticket.finished()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            if (ticket.expiresAt() <= now) {
                // Expired intent must not be applied to a fresh reconnect, and must not trap the account indefinitely.
                return write(ticket.completed(), stored, 0).thenCompose(ignored ->
                    CompletableFuture.failedFuture(new IllegalStateException("Server handover expired")));
            }
            if (!ticket.targetServer().equals(server)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Server handover expired or misrouted"));
            }
            if (ticket.claimant() != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Server handover already claimed"));
            }
            PlayerTransferTicket claimed = ticket.claimed(login);
            return write(claimed, stored, ticket.expiresAt()).thenApply(ignored -> Optional.of(claimed));
        }));
    }

    @Override
    public CompletableFuture<Void> finish(final PlayerTransferTicket ticket) {
        return load(ticket.playerId()).thenCompose(stored -> {
            PlayerTransferTicket current = decode(stored);
            if (current == null || !current.requestId().equals(ticket.requestId())
                || !Objects.equals(current.claimant(), ticket.claimant())) {
                return CompletableFuture.failedFuture(new IllegalStateException("Server handover ownership changed"));
            }
            return current.finished() ? CompletableFuture.completedFuture(null) : write(current.completed(), stored, 0);
        });
    }

    @Override
    public CompletableFuture<Boolean> cancel(final UUID playerId, final UUID requestId) {
        return load(playerId).thenCompose(stored -> {
            PlayerTransferTicket ticket = decode(stored);
            if (ticket == null || !ticket.requestId().equals(requestId) || ticket.claimant() != null) {
                return CompletableFuture.completedFuture(false);
            }
            return ticket.finished() ? CompletableFuture.completedFuture(true)
                : write(ticket.completed(), stored, 0).thenApply(ignored -> true);
        });
    }

    private CompletableFuture<StoredProfileDocument> load(final UUID playerId) {
        ProfileDocumentKey key = key(playerId);
        return storage.load(List.of(key)).thenApply(values -> values.get(key));
    }

    private CompletableFuture<Void> write(
        final PlayerTransferTicket ticket, final StoredProfileDocument previous, final long deadline
    ) {
        try {
            var change = new ProfileDocumentChange(key(ticket.playerId()), previous == null ? 0 : previous.revision(),
                mapper.writeValueAsString(ticket), deadline);
            return storage.commit(List.of(change)).thenAccept(committed -> {
                if (!committed) {
                    throw new IllegalStateException("Server handover changed concurrently");
                }
            });
        } catch (JsonProcessingException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private PlayerTransferTicket decode(final StoredProfileDocument stored) {
        if (stored == null) {
            return null;
        }
        try {
            return mapper.readValue(stored.value(), PlayerTransferTicket.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid server handover", failure);
        }
    }

    private static ProfileDocumentKey key(final UUID playerId) {
        return new ProfileDocumentKey(PlayerDataAddress.account(playerId), "vexcore", "server_transfer");
    }
}
