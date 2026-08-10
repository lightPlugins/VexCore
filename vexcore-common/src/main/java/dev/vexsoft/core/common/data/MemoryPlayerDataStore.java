package dev.vexsoft.core.common.data;


import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class MemoryPlayerDataStore implements
    PlayerDataStore,
    GlobalDataStore,
    PlayerIdentityStore {

  private final Map<String, Map<UUID, Map<String, String>>> values = new ConcurrentHashMap<>();
  private final Map<String, Map<UUID, String>> names = new ConcurrentHashMap<>();
  private final Map<GlobalDataReference, StoredGlobalData> globalValues = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<Consumer<GlobalDataReference>> globalListeners =
      new CopyOnWriteArrayList<>();
  private final AtomicLong globalRevision = new AtomicLong();
  private final Map<UUID, PlayerIdentity> identities = new ConcurrentHashMap<>();
  private final Map<String, UUID> identityNames = new ConcurrentHashMap<>();

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
  public CompletableFuture<Void> reconcileGlobalData() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Optional<StoredGlobalData>> loadGlobalData(
      final String owner,
      final String key
  ) {
    return CompletableFuture.completedFuture(Optional.ofNullable(
        globalValues.get(new GlobalDataReference(owner, key))
    ));
  }

  @Override
  public CompletableFuture<StoredGlobalData> setGlobalData(
      final String owner,
      final String key,
      final String value
  ) {
    GlobalDataReference reference = new GlobalDataReference(owner, key);
    StoredGlobalData stored = new StoredGlobalData(value, globalRevision.incrementAndGet());
    globalValues.put(reference, stored);
    notifyGlobalChange(reference);
    return CompletableFuture.completedFuture(stored);
  }

  @Override
  public CompletableFuture<Optional<StoredGlobalData>> compareAndSetGlobalData(
      final String owner,
      final String key,
      final long expectedRevision,
      final String value
  ) {
    GlobalDataReference reference = new GlobalDataReference(owner, key);
    StoredGlobalData[] result = new StoredGlobalData[1];
    globalValues.compute(reference, (ignored, current) -> {
      long currentRevision = current == null ? 0 : current.revision();
      if (currentRevision != expectedRevision) {
        return current;
      }
      result[0] = new StoredGlobalData(value, globalRevision.incrementAndGet());
      return result[0];
    });
    if (result[0] != null) {
      notifyGlobalChange(reference);
    }
    return CompletableFuture.completedFuture(Optional.ofNullable(result[0]));
  }

  @Override
  public CompletableFuture<Boolean> resetGlobalData(final String owner, final String key) {
    GlobalDataReference reference = new GlobalDataReference(owner, key);
    boolean removed = globalValues.remove(reference) != null;
    if (removed) {
      notifyGlobalChange(reference);
    }
    return CompletableFuture.completedFuture(removed);
  }

  @Override
  public AutoCloseable subscribeGlobalDataChanges(
      final Consumer<GlobalDataReference> listener
  ) {
    Consumer<GlobalDataReference> checkedListener = Objects.requireNonNull(listener, "listener");
    globalListeners.add(checkedListener);
    return () -> globalListeners.remove(checkedListener);
  }

  @Override
  public CompletableFuture<Void> reconcilePlayerIdentities() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<PlayerIdentity> recordPlayerIdentity(
      final UUID uniqueId,
      final String name
  ) {
    PlayerIdentity identity = new PlayerIdentity(uniqueId, name, Instant.now());
    PlayerIdentity previous = identities.put(uniqueId, identity);
    if (previous != null) {
      identityNames.remove(previous.name().toLowerCase(Locale.ROOT), uniqueId);
    }
    identityNames.put(name.toLowerCase(Locale.ROOT), uniqueId);
    return CompletableFuture.completedFuture(identity);
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final UUID uniqueId) {
    return CompletableFuture.completedFuture(Optional.ofNullable(identities.get(uniqueId)));
  }

  @Override
  public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final String name) {
    UUID uniqueId = identityNames.get(name.toLowerCase(Locale.ROOT));
    return CompletableFuture.completedFuture(Optional.ofNullable(uniqueId).map(identities::get));
  }

  @Override
  public void close() {
    values.clear();
    names.clear();
    globalValues.clear();
    globalListeners.clear();
    identities.clear();
    identityNames.clear();
  }

  private void notifyGlobalChange(final GlobalDataReference reference) {
    globalListeners.forEach(listener -> listener.accept(reference));
  }
}
