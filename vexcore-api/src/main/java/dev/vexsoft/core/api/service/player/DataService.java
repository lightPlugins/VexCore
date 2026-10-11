package dev.vexsoft.core.api.service.player;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDataSnapshot;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers persistent player-data definitions owned by the current plugin scope.
 *
 * <p>Registration queues asynchronous schema reconciliation. Logins and saves await readiness.
 * New account keys are installed with defaults on players that are already loaded.</p>
 */
public interface DataService extends VexService {

    /** Loads all registered participants at an explicit address without installing a live session. */
    default CompletableFuture<ProfileDataSnapshot> readProfile(final PlayerProfileContext context) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile snapshots unavailable"));
    }

    /** Serializes detached replacements with the snapshot's revisions for an atomic guarded transaction. */
    default List<ProfileDocumentChange> profileChanges(
        final ProfileDataSnapshot snapshot, final Map<DataContainerKey<?>, Object> replacements
    ) {
        throw new UnsupportedOperationException("Profile serialization unavailable");
    }

    /** Persists the loaded player's current dirty data; completion acknowledges the storage write. */
    default CompletableFuture<Void> save(UUID playerId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Explicit saves are not supported"));
    }

    /** Saves only if the supplied complete profile session is still installed; stale callbacks fail closed. */
    default CompletableFuture<Void> save(final PlayerProfileContext context) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile saves are unavailable"));
    }

    /** Reads a detached durable container without loading a session or including unsaved live changes. */
    default <T> CompletableFuture<Optional<T>> readStored(UUID playerId, DataContainerKey<T> key) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Stored reads are not supported"));
    }

    /** Reads one explicit personal-profile address, including offline or retired sessions, without live mutation. */
    default <T> CompletableFuture<Optional<T>> readStored(
        final PlayerProfileContext context, final DataContainerKey<T> key
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Addressed reads are unavailable"));
    }

    /** Freezes this owner's loaded data after feature cleanup, before its classloader closes. */
    void prepareUnload();

    /**
     * Creates and registers a player-data definition owned by this plugin.
     *
     * @param definitionType public definition class created through the scoped service registry
     * @throws IllegalStateException if definition registration fails
     */
    void register(Class<? extends PlayerDataDefinition> definitionType);
}
