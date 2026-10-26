package dev.vexsoft.core.common.service.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.network.NetworkPresenceService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Uses Core's existing authoritative document transactions and database clock for carrierless presence reads. */
@Dependencies(ProfileStorageService.class)
public final class VexNetworkPresenceService implements NetworkPresenceService {

    private static final ProfileDocumentKey KEY = new ProfileDocumentKey(
        PlayerDataAddress.shared(new ProfileDomain("vexcore:network"), new UUID(0, 1)), "vexcore", "directory");
    private static final long LEASE_MILLIS = 15_000;
    private final ProfileStorageService storage;
    private final UUID publisher = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    public VexNetworkPresenceService(final VexServiceRegistry services) {
        storage = services.require(ProfileStorageService.class);
    }

    @Override
    public CompletableFuture<List<NetworkPlayer>> snapshot() {
        return storage.load(List.of(KEY)).thenCompose(documents -> storage.databaseTime().thenApply(time -> {
            var stored = documents.get(KEY);
            if (stored == null) {
                throw new IllegalStateException("Network proxy presence is not published");
            }
            Presence presence = decode(stored.value());
            if (presence.expiresAt() <= time) {
                throw new IllegalStateException("Network proxy presence has expired");
            }
            return presence.players();
        }));
    }

    @Override
    public CompletableFuture<Void> publish(final List<NetworkPlayer> players) {
        return change(List.copyOf(players), false, 0);
    }

    @Override
    public CompletableFuture<Void> withdraw() {
        return change(List.of(), true, 0);
    }

    private CompletableFuture<Void> change(final List<NetworkPlayer> players, final boolean withdraw, final int attempt) {
        return storage.load(List.of(KEY)).thenCompose(documents -> storage.databaseTime().thenCompose(time -> {
            var stored = documents.get(KEY);
            Presence current = stored == null ? null : decode(stored.value());
            if (current != null && !current.publisher().equals(publisher)) {
                if (withdraw) {
                    return CompletableFuture.completedFuture(null);
                }
                if (current.expiresAt() > time) {
                    throw new IllegalStateException("Another proxy owns the network-directory writer lease");
                }
            }
            var replacement = new Presence(publisher, withdraw ? 0 : time + LEASE_MILLIS, players);
            var change = new ProfileDocumentChange(KEY, stored == null ? 0 : stored.revision(), encode(replacement));
            return storage.commit(List.of(change)).thenCompose(committed -> {
                if (committed) {
                    return CompletableFuture.completedFuture(null);
                }
                if (attempt >= 15) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Network presence changed too often"));
                }
                return change(players, withdraw, attempt + 1);
            });
        }));
    }

    private Presence decode(final String json) {
        try {
            return mapper.readValue(json, Presence.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid network proxy presence", failure);
        }
    }

    private String encode(final Presence presence) {
        try {
            return mapper.writeValueAsString(presence);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Cannot serialize network proxy presence", failure);
        }
    }

    private record Presence(UUID publisher, long expiresAt, List<NetworkPlayer> players) {

        private Presence {
            players = List.copyOf(players);
        }
    }
}
