package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryPlayerDataStore implements PlayerDataStore {

  private final Map<String, Map<UUID, Map<String, String>>> values = new ConcurrentHashMap<>();
  private final Map<String, Map<UUID, String>> names = new ConcurrentHashMap<>();

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
    names.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).put(uniqueId, playerName);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Integer> reset(
      final String owner,
      final UUID uniqueId,
      final Collection<String> containers
  ) {
    Map<String, String> stored = values
        .getOrDefault(owner, Map.of())
        .get(uniqueId);
    if (stored == null) {
      return CompletableFuture.completedFuture(0);
    }
    containers.forEach(stored::remove);
    return CompletableFuture.completedFuture(1);
  }

  @Override
  public CompletableFuture<Integer> resetAll(
      final String owner,
      final Collection<String> containers
  ) {
    Map<UUID, Map<String, String>> stored = values.get(owner);
    if (stored == null) {
      return CompletableFuture.completedFuture(0);
    }
    stored.values().forEach(player -> containers.forEach(player::remove));
    return CompletableFuture.completedFuture(stored.size());
  }

  @Override
  public CompletableFuture<Optional<UUID>> findUniqueId(
      final String owner,
      final String playerName
  ) {
    return CompletableFuture.completedFuture(names.getOrDefault(owner, Map.of()).entrySet().stream()
        .filter(entry -> entry.getValue().equalsIgnoreCase(playerName))
        .map(Map.Entry::getKey)
        .findFirst());
  }

  @Override
  public void close() {
    values.clear();
    names.clear();
  }
}
