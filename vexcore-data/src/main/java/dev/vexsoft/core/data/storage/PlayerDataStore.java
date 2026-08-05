package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists plugin-owned player containers in a storage backend
 */
public interface PlayerDataStore extends AutoCloseable {

  /** Adds missing storage structures for the supplied containers */
  CompletableFuture<Void> reconcile(String owner, Collection<DataContainerKey<?>> keys);

  /** Loads the stored JSON values for one plugin and player */
  CompletableFuture<Map<String, String>> load(
      String owner,
      UUID uniqueId,
      Collection<DataContainerKey<?>> keys
  );

  /** Saves JSON values for one plugin and player */
  CompletableFuture<Void> save(
      String owner,
      UUID uniqueId,
      String playerName,
      Map<String, String> values
  );

  @Override
  void close();
}
