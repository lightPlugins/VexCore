package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDataSnapshot;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Coordinates registered containers, cached players and persistence operations */
public interface PlayerDataCoordinatorService extends VexService {

    /** Keeps the snapshot executor available until the asynchronous platform shutdown drain completes. */
    default void drainBeforeClose(final CompletableFuture<Void> drain) {
    }

    /** Installs the optional platform capture boundary used by public data-service saves. */
    default AutoCloseable onCapture(
        final BiFunction<VexPlayer, PlayerProfileContext, CompletableFuture<Void>> capture
    ) {
        return () -> {
        };
    }

    /** Includes optional native capture while retaining account-only save behavior. */
    default CompletableFuture<Void> saveCaptured(final UUID playerId, final PlayerProfileContext expected) {
        return expected == null ? save(playerId) : save(expected);
    }

    /** Prepares detached revision-bound writes without touching a loaded player. */
    default List<ProfileDocumentChange> profileChanges(
        final ProfileDataSnapshot snapshot, final Map<DataContainerKey<?>, Object> replacements
    ) {
        throw new UnsupportedOperationException("Profile serialization unavailable");
    }

    /** Captures owner data as classloader-independent JSON before plugin unload. */
    void prepareUnload(ServiceOwner owner);

    /**
     * Writes operator-reviewable recovery files for still-dirty loaded players after a failed save.
     */
    default void exportRecovery(final Path directory) {
        throw new UnsupportedOperationException("Player recovery export is unavailable");
    }

    /** Registers every container declared by a plugin data definition */
    void register(ServiceOwner owner, PlayerDataDefinition definition);

    /** Registers a player feature container owned by a plugin. */
    <T extends PlayerContainer> void registerContainer(
        ServiceOwner owner,
        Class<T> type,
        PlayerContainerFactory<? extends T> factory
    );

    /** Removes and closes every player feature container registered by an owner. */
    void unregisterContainers(ServiceOwner owner);

    /** Creates or refreshes a cached player with every registered container */
    VexPlayer create(UUID uniqueId, String name);

    /** Loads a player and places it in the shared cache */
    CompletableFuture<VexPlayer> load(UUID uniqueId, String name);

    /** Loads participating keys into a detached snapshot without changing the active player session. */
    default CompletableFuture<ProfileDataSnapshot> loadProfile(PlayerProfileContext context) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile loading is unavailable"));
    }

    /** Installs a fully loaded profile on the platform's player execution context. */
    default void installProfile(VexPlayer player, ProfileDataSnapshot snapshot) {
        throw new UnsupportedOperationException("Profile installation is unavailable");
    }

    /** Installs persistent membership guards supplied by the participating game. */
    default void installProfile(
        final VexPlayer player, final ProfileDataSnapshot snapshot, final List<ProfileDocumentChange> guards
    ) {
        installProfile(player, snapshot);
    }

    /** Finds a player in the shared online cache */
    Optional<VexPlayer> find(UUID uniqueId);

    /** Returns a snapshot of every currently loaded player session. */
    Collection<VexPlayer> getLoadedPlayers();

    /** Removes a player from the shared online cache */
    Optional<VexPlayer> remove(UUID uniqueId);

    /** Saves every changed container before removing a cached player */
    CompletableFuture<Void> saveAndRemove(UUID uniqueId);

    /** Saves every changed container while retaining the cached player. */
    CompletableFuture<Void> save(UUID uniqueId);

    /** Atomically commits extra revision-bound documents with this session's scoped dirty containers. */
    default CompletableFuture<Void> save(final UUID uniqueId, final List<ProfileDocumentChange> changes) {
        if (!changes.isEmpty()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile transactions unavailable"));
        }
        return save(uniqueId);
    }

    /** Saves the captured complete profile session and rejects a retired generation. */
    default CompletableFuture<Void> save(final PlayerProfileContext context) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile saves are unavailable"));
    }

    /** Saves every changed container of every cached player */
    CompletableFuture<Void> saveAll();

    /** Reads a detached persisted value scoped to a registered owner without creating a player session. */
    default <T> CompletableFuture<Optional<T>> readStored(ServiceOwner owner, UUID playerId, DataContainerKey<T> key) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Stored reads are not supported"));
    }

    /** Reads a detached profile container using its original explicit address. */
    default <T> CompletableFuture<Optional<T>> readStored(
        final ServiceOwner owner, final PlayerProfileContext context, final DataContainerKey<T> key
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Addressed reads are unavailable"));
    }

    /** Returns all container keys registered by an owner */
    Collection<DataContainerKey<?>> getKeys(ServiceOwner owner);

    /** Returns the user-facing names of every registered persistent container. */
    Collection<String> getContainerIds();

    /** Returns the number of registered player-facing feature containers. */
    int getFeatureContainerCount();

    /** Resets one registered container for one player. */
    CompletableFuture<Void> resetPlayerContainer(UUID uniqueId, String containerId);

    /** Resets every registered container for one player. */
    CompletableFuture<Void> resetPlayerContainers(UUID uniqueId);

    /** Resets one registered container for all stored and loaded players. */
    CompletableFuture<Void> resetGlobalContainer(String containerId);

    /** Resets every registered container for all stored and loaded players. */
    CompletableFuture<Void> resetGlobalContainers();

    /** Resolves a loaded or stored player identity from a name or UUID. */
    CompletableFuture<Optional<UUID>> resolveUniqueId(String player);
}
