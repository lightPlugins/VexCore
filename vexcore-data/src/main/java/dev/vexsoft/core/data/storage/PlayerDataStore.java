package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

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

  /** Resets selected containers for one player while preserving the player row. */
  default CompletableFuture<Integer> reset(
      String owner,
      UUID uniqueId,
      Collection<String> containers
  ) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Player data resets are not supported")
    );
  }

  /** Resets selected containers for every stored player of one owner. */
  default CompletableFuture<Integer> resetAll(
      String owner,
      Collection<String> containers
  ) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Global player data resets are not supported")
    );
  }

  /** Finds the most recently stored identity matching a case-insensitive player name. */
  default CompletableFuture<Optional<UUID>> findUniqueId(
      String owner,
      String playerName
  ) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  void close();
}
