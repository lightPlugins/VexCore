package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Objects;

/** Reuses the configured Core storage backend and its asynchronous readiness boundary. */
@Dependencies(PlayerDataStoreService.class)
public final class VexProfileStorageService implements ProfileStorageService {

    private final ProfileDocumentStore store;
    private final CompletableFuture<Void> ready;

    public VexProfileStorageService(final VexServiceRegistry services) {
        PlayerDataStoreService storage = Objects.requireNonNull(services, "services")
            .require(PlayerDataStoreService.class);
        store = (ProfileDocumentStore) storage.getStore();
        ready = storage.getReadiness().thenCompose(ignored -> store.reconcileProfileDocuments());
    }

    @Override
    public CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> load(
        final Collection<ProfileDocumentKey> keys
    ) {
        return ready.thenCompose(ignored -> store.loadProfileDocuments(keys));
    }

    @Override
    public CompletableFuture<Boolean> commit(final Collection<ProfileDocumentChange> changes) {
        return ready.thenCompose(ignored -> store.commitProfileDocuments(changes));
    }

    @Override
    public CompletableFuture<Long> databaseTime() {
        return ready.thenCompose(ignored -> store.profileDatabaseTime());
    }
}
