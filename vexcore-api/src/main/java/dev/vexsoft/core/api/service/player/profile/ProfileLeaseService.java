package dev.vexsoft.core.api.service.player.profile;

import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns fenced writable personal-profile sessions independently of any game's membership rules. */
public interface ProfileLeaseService extends VexService {

    /** Keeps ownership valid until the platform has drained its in-flight saves during shutdown. */
    default void drainBeforeClose(final CompletableFuture<Void> drain) {
    }

    /** Registers a platform-owned drain callback for remote retirement requests. */
    AutoCloseable onRetirement(Function<PlayerProfileContext, CompletableFuture<Void>> handler);

    /** Drains live ownership and returns revision guards proving this explicit address is offline. */
    CompletableFuture<List<ProfileDocumentChange>> retire(PlayerDataAddress address);

    /** Acquires exclusive ownership or returns empty when another live server session owns the address. */
    CompletableFuture<Optional<PlayerProfileContext>> acquire(
        ProfileDomain domain, UUID profileId, UUID playerId, UUID sessionId, long generation, long membershipEpoch);

    /** Returns a stable transaction guard; expired or retired ownership fails closed. */
    ProfileDocumentChange writeGuard(PlayerProfileContext context);

    /** Releases exactly the captured ownership; another generation's lease is never released. */
    CompletableFuture<Void> release(PlayerProfileContext context);

    /** Checks cached readiness without issuing any database request. */
    boolean isWritable(PlayerProfileContext context);
}
