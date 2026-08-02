package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryPlayerDataStore implements PlayerDataStore {

  private final Map<String, Map<UUID, Map<String, String>>> values = new ConcurrentHashMap<>();

  @Override
  public CompletableFuture<Void> reconcile(
      final String owner,
      final Collection<DataContainerKey<?>> keys
  ) {
    values.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>());
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Map<String, String>> load(
      final String owner,
      final UUID uniqueId,
      final Collection<DataContainerKey<?>> keys
  ) {
    Map<String, String> stored = values
        .getOrDefault(owner, Map.of())
        .getOrDefault(uniqueId, Map.of());
    return CompletableFuture.completedFuture(Map.copyOf(stored));
  }

  @Override
  public CompletableFuture<Void> save(
      final String owner,
      final UUID uniqueId,
      final String playerName,
      final Map<String, String> updatedValues
  ) {
    values.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>())
        .computeIfAbsent(uniqueId, ignored -> new ConcurrentHashMap<>())
        .putAll(updatedValues);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void close() {
    values.clear();
  }
}
