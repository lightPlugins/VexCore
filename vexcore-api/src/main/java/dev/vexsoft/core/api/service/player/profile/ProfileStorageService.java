package dev.vexsoft.core.api.service.player.profile;

import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/** Provides asynchronous, revision-checked transactions across explicit persistence addresses. */
public interface ProfileStorageService extends VexService {

    /** Loads detached documents; absent keys have revision zero for their first write. */
    CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> load(Collection<ProfileDocumentKey> keys);

    /** Commits every change atomically; false means no change was committed because a revision conflicted. */
    CompletableFuture<Boolean> commit(Collection<ProfileDocumentChange> changes);

    /** Reads authoritative database time for leases and expiration rules. */
    CompletableFuture<Long> databaseTime();
}
