package dev.vexsoft.core.common.data.profile;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/** Provides bounded asynchronous revision transactions using the configured player-data backend. */
public interface ProfileDocumentStore {

    /** Creates missing indexed profile structures without blocking the caller. */
    CompletableFuture<Void> reconcileProfileDocuments();

    /** Loads exactly the supplied addresses; missing documents have revision zero. */
    CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> loadProfileDocuments(
        Collection<ProfileDocumentKey> keys
    );

    /** Commits all checks and writes together, returning false without partial writes on conflict. */
    CompletableFuture<Boolean> commitProfileDocuments(Collection<ProfileDocumentChange> changes);

    /** Reads the storage authority's time for leases and expiration decisions. */
    CompletableFuture<Long> profileDatabaseTime();
}
