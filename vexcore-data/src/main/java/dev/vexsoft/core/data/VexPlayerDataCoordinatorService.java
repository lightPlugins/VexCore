package dev.vexsoft.core.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.data.storage.PlayerDataStore;
import dev.vexsoft.core.data.storage.PlayerDataStoreService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Dependencies(PlayerDataStoreService.class)
public final class VexPlayerDataCoordinatorService implements PlayerDataCoordinatorService {

  private final Map<UUID, VexPlayer> players = new ConcurrentHashMap<>();
  private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
  private final Map<String, OwnerContainers> containersByOwner = new LinkedHashMap<>();
  private final Object saveLock = new Object();
  private final PlayerDataStore store;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  public VexPlayerDataCoordinatorService(final VexServiceRegistry services) {
    store = Objects.requireNonNull(services, "services")
        .require(PlayerDataStoreService.class)
        .getStore();
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final PlayerDataDefinition definition
  ) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(definition, "definition");
    String ownerName = normalizeOwner(owner.getServiceOwnerName());
    OwnerContainers ownerContainers = containersByOwner.computeIfAbsent(
        ownerName,
        ignored -> new OwnerContainers(owner)
    );
    if (ownerContainers.owner != owner) {
      throw new IllegalStateException("Player data owner name is already registered: " + ownerName);
    }

    List<DataContainerKey<?>> added = new ArrayList<>();
    DataContainerRegistry registry = key -> {
      Objects.requireNonNull(key, "key");
      DataContainerKey<?> existing = ownerContainers.keys.putIfAbsent(key.getName(), key);
      if (existing != null && existing != key) {
        throw new IllegalStateException(
            "Player container is already registered: " + ownerName + ":" + key.getName()
        );
      }
      if (existing == null) {
        added.add(key);
      }
    };

    try {
      definition.register(registry);
      // Schema reconciliation belongs to startup so logins never see a partial schema
      store.reconcile(ownerName, ownerContainers.keys.values()).join();
    } catch (RuntimeException exception) {
      for (DataContainerKey<?> key : added) {
        ownerContainers.keys.remove(key.getName(), key);
      }
      throw exception;
    }

    for (VexPlayer player : players.values()) {
      for (DataContainerKey<?> key : added) {
        installDefault(player, key, true);
      }
    }
  }

  @Override
  public synchronized VexPlayer create(final UUID uniqueId, final String name) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    Objects.requireNonNull(name, "name");
    VexPlayer player = players.computeIfAbsent(uniqueId, ignored -> new VexPlayer(uniqueId, name));
    player.setName(name);
    for (OwnerContainers owner : containersByOwner.values()) {
      for (DataContainerKey<?> key : owner.keys.values()) {
        installDefault(player, key, true);
      }
    }
    return player;
  }

  @Override
  public CompletableFuture<VexPlayer> load(final UUID uniqueId, final String name) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    Objects.requireNonNull(name, "name");
    CompletableFuture<Void> previousSave;
    synchronized (saveLock) {
      previousSave = saveChains.get(uniqueId);
    }
    if (previousSave != null) {
      return previousSave.thenCompose(ignored -> load(uniqueId, name));
    }
    Map<String, OwnerContainers> owners;
    synchronized (this) {
      owners = new LinkedHashMap<>(containersByOwner);
    }
    Map<DataContainerKey<?>, Object> loaded = new ConcurrentHashMap<>();
    CompletableFuture<?>[] loads = owners.entrySet().stream()
        .map(entry -> store.load(entry.getKey(), uniqueId, entry.getValue().keys.values())
            .thenAccept(values -> readOwnerValues(entry.getValue(), values, loaded)))
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(loads).thenApply(ignored -> {
      VexPlayer player = new VexPlayer(uniqueId, name);
      for (OwnerContainers owner : owners.values()) {
        for (DataContainerKey<?> key : owner.keys.values()) {
          Object value = loaded.get(key);
          if (value == null) {
            installDefault(player, key, true);
          } else {
            installLoaded(player, key, value);
          }
        }
      }
      VexPlayer previous = players.putIfAbsent(uniqueId, player);
      return previous == null ? player : previous;
    });
  }

  @Override
  public Optional<VexPlayer> find(final UUID uniqueId) {
    return Optional.ofNullable(players.get(Objects.requireNonNull(uniqueId, "uniqueId")));
  }

  @Override
  public Optional<VexPlayer> remove(final UUID uniqueId) {
    return Optional.ofNullable(players.remove(Objects.requireNonNull(uniqueId, "uniqueId")));
  }

  @Override
  public CompletableFuture<Void> saveAndRemove(final UUID uniqueId) {
    VexPlayer player = players.get(Objects.requireNonNull(uniqueId, "uniqueId"));
    if (player == null) {
      return CompletableFuture.completedFuture(null);
    }
    CompletableFuture<Void> save = queueSave(player);
    save.whenComplete((ignored, throwable) -> players.remove(uniqueId, player));
    return save;
  }

  @Override
  public CompletableFuture<Void> saveAll() {
    CompletableFuture<?>[] saves = players.keySet().stream()
        .map(players::get)
        .filter(Objects::nonNull)
        .map(this::queueSave)
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(saves);
  }

  @Override
  public synchronized Collection<DataContainerKey<?>> getKeys(final ServiceOwner owner) {
    String ownerName = normalizeOwner(Objects.requireNonNull(owner, "owner").getServiceOwnerName());
    OwnerContainers ownerContainers = containersByOwner.get(ownerName);
    if (ownerContainers == null || ownerContainers.owner != owner) {
      return List.of();
    }
    return List.copyOf(ownerContainers.keys.values());
  }

  private void readOwnerValues(
      final OwnerContainers owner,
      final Map<String, String> values,
      final Map<DataContainerKey<?>, Object> target
  ) {
    for (DataContainerKey<?> key : owner.keys.values()) {
      String json = values.get(key.getName());
      if (json == null) {
        continue;
      }
      try {
        target.put(key, objectMapper.readValue(json, key.getType()));
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Unable to read player container " + key.getName(), exception);
      }
    }
  }

  private CompletableFuture<Void> saveOwner(
      final VexPlayer player,
      final String ownerName,
      final OwnerContainers owner
  ) {
    Map<String, String> values = new LinkedHashMap<>();
    Map<DataContainerKey<?>, Long> revisions = new LinkedHashMap<>();
    Collection<DataContainerKey<?>> dirtyKeys = player.getDirtyKeys();
    for (DataContainerKey<?> key : owner.keys.values()) {
      if (!dirtyKeys.contains(key)) {
        continue;
      }
      VexPlayer.ContainerSnapshot<String> snapshot = player.snapshot(key, value -> {
        try {
          return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Unable to write player container " + key.getName(), exception);
        }
      });
      values.put(key.getName(), snapshot.getValue());
      revisions.put(key, snapshot.getRevision());
    }
    return store.save(ownerName, player.getUniqueId(), player.getName(), values).thenRun(() -> {
      for (Map.Entry<DataContainerKey<?>, Long> revision : revisions.entrySet()) {
        player.markClean(revision.getKey(), revision.getValue());
      }
    });
  }

  private CompletableFuture<Void> saveNow(final VexPlayer player) {
    Map<String, OwnerContainers> owners;
    synchronized (this) {
      owners = new LinkedHashMap<>(containersByOwner);
    }
    CompletableFuture<?>[] saves = owners.entrySet().stream()
        .map(entry -> saveOwner(player, entry.getKey(), entry.getValue()))
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(saves);
  }

  private CompletableFuture<Void> queueSave(final VexPlayer player) {
    UUID uniqueId = player.getUniqueId();
    CompletableFuture<Void> next;
    synchronized (saveLock) {
      CompletableFuture<Void> previous = saveChains.get(uniqueId);
      CompletableFuture<Void> ready = previous == null
          ? CompletableFuture.completedFuture(null)
          : previous.handle((ignored, throwable) -> null);
      next = ready.thenCompose(ignored -> saveNow(player));
      saveChains.put(uniqueId, next);
    }
    CompletableFuture<Void> queued = next;
    queued.whenComplete((ignored, throwable) -> {
      synchronized (saveLock) {
        saveChains.remove(uniqueId, queued);
      }
    });
    return queued;
  }

  private static String normalizeOwner(final String ownerName) {
    String normalized = Objects.requireNonNull(ownerName, "ownerName")
        .trim()
        .toLowerCase(java.util.Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,50}")) {
      throw new IllegalArgumentException("Invalid player data owner name: " + ownerName);
    }
    return normalized;
  }

  private static <T> void installDefault(
      final VexPlayer player,
      final DataContainerKey<T> key,
      final boolean dirty
  ) {
    if (!player.has(key)) {
      player.install(key, key.createDefaultValue(), dirty);
    }
  }

  private static <T> void installLoaded(
      final VexPlayer player,
      final DataContainerKey<T> key,
      final Object value
  ) {
    player.install(key, key.getType().cast(value), false);
  }

  private static final class OwnerContainers {

    private final ServiceOwner owner;
    private final Map<String, DataContainerKey<?>> keys = new LinkedHashMap<>();

    private OwnerContainers(final ServiceOwner owner) {
      this.owner = owner;
    }
  }
}
