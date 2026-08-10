package dev.vexsoft.core.common.data.global;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Persists revisioned plugin-owned values shared by the entire network. */
public interface GlobalDataStore {

  /** Creates missing global-data storage structures. */
  CompletableFuture<Void> reconcileGlobalData();

  /** Loads one stored value. */
  CompletableFuture<Optional<StoredGlobalData>> loadGlobalData(String owner, String key);

  /** Stores one value regardless of its current revision. */
  CompletableFuture<StoredGlobalData> setGlobalData(String owner, String key, String value);

  /** Stores one value only when its current revision matches the expected revision. */
  CompletableFuture<Optional<StoredGlobalData>> compareAndSetGlobalData(
      String owner,
      String key,
      long expectedRevision,
      String value
  );

  /** Removes one stored value. */
  CompletableFuture<Boolean> resetGlobalData(String owner, String key);

  /** Subscribes to local and remote invalidation notifications. */
  AutoCloseable subscribeGlobalDataChanges(Consumer<GlobalDataReference> listener);
}
