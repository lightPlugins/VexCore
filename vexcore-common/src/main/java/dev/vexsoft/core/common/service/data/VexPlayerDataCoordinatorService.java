package dev.vexsoft.core.common.service.data;


import java.util.Locale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.storage.PlayerDataStore;
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
import java.time.Duration;
import lombok.Value;

@Dependencies({PlayerDataStoreService.class, CacheService.class})
public final class VexPlayerDataCoordinatorService implements PlayerDataCoordinatorService {

  private final Map<UUID, VexPlayer> players = new ConcurrentHashMap<>();
  private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
  private final Map<String, OwnerContainers> containersByOwner = new LinkedHashMap<>();
  private final Map<Class<? extends PlayerContainer>, RegisteredContainer<?>> featureContainers =
      new LinkedHashMap<>();
  private volatile Map<Class<? extends PlayerContainer>, Integer> featureContainerSlotSnapshot =
      Map.of();
  private final ClassValue<Integer> featureContainerSlots = new ClassValue<>() {
    @Override
    protected Integer computeValue(final Class<?> type) {
      if (!PlayerContainer.class.isAssignableFrom(type)) {
        return -1;
      }
      return featureContainerSlotSnapshot.getOrDefault(type, -1);
    }
  };
  private int nextFeatureContainerSlot;
  private final Object saveLock = new Object();
  private final PlayerDataStore store;
  private final VexAsyncCache<PlayerLoadRequest, VexPlayer> playerLoads;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  public VexPlayerDataCoordinatorService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    store = checkedServices.require(PlayerDataStoreService.class).getStore();
    playerLoads = checkedServices.require(CacheService.class).createAsync(
        "player-loads",
        VexCacheOptions.builder()
            .maximumSize(1_000L)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build(),
        this::loadFromStore
    );
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
  public synchronized <T extends PlayerContainer> void registerContainer(
      final ServiceOwner owner,
      final Class<T> type,
      final PlayerContainerFactory<? extends T> factory
  ) {
    Objects.requireNonNull(owner, "owner");
    Class<T> checkedType = Objects.requireNonNull(type, "type");
    PlayerContainerFactory<? extends T> checkedFactory = Objects.requireNonNull(
        factory,
        "factory"
    );
    if (featureContainers.containsKey(checkedType)) {
      throw new IllegalStateException(
          "Player feature container is already registered: " + checkedType.getName()
      );
    }
    RegisteredContainer<T> registered = new RegisteredContainer<>(
        owner,
        checkedType,
        checkedFactory,
        nextFeatureContainerSlot++
    );
    featureContainers.put(checkedType, registered);
    refreshFeatureContainerSlotSnapshot();
    featureContainerSlots.remove(checkedType);
    List<VexPlayer> installed = new ArrayList<>();
    try {
      for (VexPlayer player : players.values()) {
        installContainer(player, registered);
        installed.add(player);
      }
    } catch (RuntimeException exception) {
      featureContainers.remove(checkedType, registered);
      refreshFeatureContainerSlotSnapshot();
      featureContainerSlots.remove(checkedType);
      for (VexPlayer player : installed) {
        player.removeContainer(registered.slot);
      }
      throw exception;
    }
  }

  @Override
  public synchronized void unregisterContainers(final ServiceOwner owner) {
    Objects.requireNonNull(owner, "owner");
    List<RegisteredContainer<?>> removed = featureContainers.values().stream()
        .filter(container -> container.owner == owner)
        .toList();
    for (RegisteredContainer<?> container : removed) {
      featureContainers.remove(container.type, container);
    }
    refreshFeatureContainerSlotSnapshot();
    for (RegisteredContainer<?> container : removed) {
      featureContainerSlots.remove(container.type);
      for (VexPlayer player : players.values()) {
        player.removeContainer(container.slot);
      }
    }
  }

  @Override
  public synchronized VexPlayer create(final UUID uniqueId, final String name) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    Objects.requireNonNull(name, "name");
    VexPlayer player = players.computeIfAbsent(
        uniqueId,
        ignored -> new VexPlayer(uniqueId, name, this::findContainerSlot)
    );
    player.setName(name);
    for (OwnerContainers owner : containersByOwner.values()) {
      for (DataContainerKey<?> key : owner.keys.values()) {
        installDefault(player, key, true);
      }
    }
    installMissingContainers(player);
    return player;
  }

  @Override
  public CompletableFuture<VexPlayer> load(final UUID uniqueId, final String name) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    Objects.requireNonNull(name, "name");
    VexPlayer existing = players.get(uniqueId);
    if (existing != null) {
      existing.setName(name);
      return CompletableFuture.completedFuture(existing);
    }
    CompletableFuture<Void> previousSave;
    synchronized (saveLock) {
      previousSave = saveChains.get(uniqueId);
    }
    if (previousSave != null) {
      return previousSave.thenCompose(ignored -> load(uniqueId, name));
    }
    PlayerLoadRequest request = new PlayerLoadRequest(uniqueId, name);
    return playerLoads.get(request)
        .thenApply(player -> {
          player.setName(name);
          return player;
        })
        .whenComplete((player, throwable) -> playerLoads.invalidate(request));
  }

  private CompletableFuture<VexPlayer> loadFromStore(final PlayerLoadRequest request) {
    VexPlayer existing = players.get(request.getUniqueId());
    if (existing != null) {
      return CompletableFuture.completedFuture(existing);
    }
    Map<String, OwnerContainers> owners;
    synchronized (this) {
      owners = new LinkedHashMap<>(containersByOwner);
    }
    Map<DataContainerKey<?>, Object> loaded = new ConcurrentHashMap<>();
    CompletableFuture<?>[] loads = owners.entrySet().stream()
        .map(entry -> store.load(entry.getKey(), request.getUniqueId(), entry.getValue().keys.values())
            .thenAccept(values -> readOwnerValues(entry.getValue(), values, loaded)))
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(loads).thenApply(ignored -> {
      VexPlayer player = new VexPlayer(
          request.getUniqueId(),
          request.getName(),
          this::findContainerSlot
      );
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
      installMissingContainers(player);
      VexPlayer previous = players.putIfAbsent(request.getUniqueId(), player);
      if (previous != null) {
        player.closeContainers();
        return previous;
      }
      return player;
    });
  }

  @Override
  public Optional<VexPlayer> find(final UUID uniqueId) {
    return Optional.ofNullable(players.get(Objects.requireNonNull(uniqueId, "uniqueId")));
  }

  @Override
  public Optional<VexPlayer> remove(final UUID uniqueId) {
    VexPlayer removed = players.remove(Objects.requireNonNull(uniqueId, "uniqueId"));
    if (removed != null) {
      removed.closeContainers();
    }
    return Optional.ofNullable(removed);
  }

  @Override
  public CompletableFuture<Void> saveAndRemove(final UUID uniqueId) {
    VexPlayer player = players.get(Objects.requireNonNull(uniqueId, "uniqueId"));
    if (player == null) {
      return CompletableFuture.completedFuture(null);
    }
    CompletableFuture<Void> save = queueSave(player);
    save.whenComplete((ignored, throwable) -> {
      if (players.remove(uniqueId, player)) {
        player.closeContainers();
      }
    });
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

  @Override
  public synchronized Collection<String> getContainerIds() {
    return containersByOwner.values().stream()
        .flatMap(owner -> owner.keys.keySet().stream())
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  public synchronized int getFeatureContainerCount() {
    return featureContainers.size();
  }

  @Override
  public CompletableFuture<Void> resetPlayerContainer(
      final UUID uniqueId,
      final String containerId
  ) {
    UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    ContainerSelection selection = requireContainer(containerId);
    VexPlayer loaded = players.get(checkedUniqueId);
    if (loaded != null) {
      loaded.reset(selection.key);
    }
    CompletableFuture<?> reset = store.reset(
        selection.owner,
        checkedUniqueId,
        List.of(selection.key.getName())
    );
    return loaded == null
        ? reset.thenApply(ignored -> null)
        : CompletableFuture.allOf(reset, queueSave(loaded));
  }

  @Override
  public CompletableFuture<Void> resetPlayerContainers(final UUID uniqueId) {
    UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    Map<String, OwnerContainers> owners = ownerSnapshot();
    VexPlayer loaded = players.get(checkedUniqueId);
    if (loaded != null) {
      resetPlayer(loaded, owners);
    }
    CompletableFuture<?>[] resets = owners.entrySet().stream()
        .map(entry -> store.reset(
            entry.getKey(),
            checkedUniqueId,
            entry.getValue().keys.keySet()
        ))
        .toArray(CompletableFuture[]::new);
    CompletableFuture<Void> stored = CompletableFuture.allOf(resets);
    return loaded == null ? stored : CompletableFuture.allOf(stored, queueSave(loaded));
  }

  @Override
  public CompletableFuture<Void> resetGlobalContainer(final String containerId) {
    ContainerSelection selection = requireContainer(containerId);
    List<VexPlayer> loaded = List.copyOf(players.values());
    for (VexPlayer player : loaded) {
      player.reset(selection.key);
    }
    CompletableFuture<?> reset = store.resetAll(
        selection.owner,
        List.of(selection.key.getName())
    );
    return CompletableFuture.allOf(reset, savePlayers(loaded));
  }

  @Override
  public CompletableFuture<Void> resetGlobalContainers() {
    Map<String, OwnerContainers> owners = ownerSnapshot();
    List<VexPlayer> loaded = List.copyOf(players.values());
    for (VexPlayer player : loaded) {
      resetPlayer(player, owners);
    }
    CompletableFuture<?>[] resets = owners.entrySet().stream()
        .map(entry -> store.resetAll(entry.getKey(), entry.getValue().keys.keySet()))
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(
        CompletableFuture.allOf(resets),
        savePlayers(loaded)
    );
  }

  @Override
  public CompletableFuture<Optional<UUID>> resolveUniqueId(final String player) {
    String checkedPlayer = Objects.requireNonNull(player, "player").trim();
    try {
      return CompletableFuture.completedFuture(Optional.of(UUID.fromString(checkedPlayer)));
    } catch (IllegalArgumentException ignored) {
      Optional<UUID> loaded = players.values().stream()
          .filter(value -> value.getName().equalsIgnoreCase(checkedPlayer))
          .map(VexPlayer::getUniqueId)
          .findFirst();
      if (loaded.isPresent()) {
        return CompletableFuture.completedFuture(loaded);
      }
    }
    List<String> owners;
    synchronized (this) {
      owners = List.copyOf(containersByOwner.keySet());
    }
    CompletableFuture<Optional<UUID>> result = CompletableFuture.completedFuture(Optional.empty());
    for (String owner : owners) {
      result = result.thenCompose(found -> found.isPresent()
          ? CompletableFuture.completedFuture(found)
          : store.findUniqueId(owner, checkedPlayer));
    }
    return result;
  }

  private synchronized ContainerSelection requireContainer(final String containerId) {
    String checkedName = Objects.requireNonNull(containerId, "containerId")
        .trim()
        .toLowerCase(Locale.ROOT);
    ContainerSelection found = null;
    for (Map.Entry<String, OwnerContainers> entry : containersByOwner.entrySet()) {
      DataContainerKey<?> key = entry.getValue().keys.get(checkedName);
      if (key == null) {
        continue;
      }
      if (found != null) {
        throw new IllegalArgumentException(
            "Player data container name is ambiguous: " + checkedName
        );
      }
      found = new ContainerSelection(entry.getKey(), key);
    }
    if (found == null) {
      throw new IllegalArgumentException(
          "Player data container is not registered: " + checkedName
      );
    }
    return found;
  }

  private synchronized Map<String, OwnerContainers> ownerSnapshot() {
    return new LinkedHashMap<>(containersByOwner);
  }

  private static void resetPlayer(
      final VexPlayer player,
      final Map<String, OwnerContainers> owners
  ) {
    for (OwnerContainers owner : owners.values()) {
      for (DataContainerKey<?> key : owner.keys.values()) {
        player.reset(key);
      }
    }
  }

  private CompletableFuture<Void> savePlayers(final Collection<VexPlayer> loaded) {
    CompletableFuture<?>[] saves = loaded.stream()
        .map(this::queueSave)
        .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(saves);
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
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,50}")) {
      throw new IllegalArgumentException("Invalid player data owner name: " + ownerName);
    }
    return normalized;
  }

  private int findContainerSlot(final Class<? extends PlayerContainer> type) {
    return featureContainerSlots.get(type);
  }

  private void refreshFeatureContainerSlotSnapshot() {
    Map<Class<? extends PlayerContainer>, Integer> slots = new LinkedHashMap<>();
    for (RegisteredContainer<?> container : featureContainers.values()) {
      slots.put(container.type, container.slot);
    }
    featureContainerSlotSnapshot = Map.copyOf(slots);
  }

  private synchronized void installMissingContainers(final VexPlayer player) {
    for (RegisteredContainer<?> container : featureContainers.values()) {
      if (player.findContainer(container.type).isEmpty()) {
        installContainerUnchecked(player, container);
      }
    }
  }

  private static <T extends PlayerContainer> void installContainer(
      final VexPlayer player,
      final RegisteredContainer<T> registered
  ) {
    T container = registered.type.cast(Objects.requireNonNull(
        registered.factory.create(player),
        "Player container factory returned null for " + registered.type.getName()
    ));
    player.installContainer(registered.slot, registered.type, container);
  }

  @SuppressWarnings("unchecked")
  private static void installContainerUnchecked(
      final VexPlayer player,
      final RegisteredContainer<?> registered
  ) {
    installContainer(player, (RegisteredContainer<PlayerContainer>) registered);
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

  private static final class RegisteredContainer<T extends PlayerContainer> {

    private final ServiceOwner owner;
    private final Class<T> type;
    private final PlayerContainerFactory<? extends T> factory;
    private final int slot;

    private RegisteredContainer(
        final ServiceOwner owner,
        final Class<T> type,
        final PlayerContainerFactory<? extends T> factory,
        final int slot
    ) {
      this.owner = owner;
      this.type = type;
      this.factory = factory;
      this.slot = slot;
    }
  }

  private record ContainerSelection(String owner, DataContainerKey<?> key) {
  }

  @Value
  private static class PlayerLoadRequest {

    UUID uniqueId;
    String name;

    @Override
    public boolean equals(final Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof PlayerLoadRequest request)) {
        return false;
      }
      return uniqueId.equals(request.uniqueId);
    }

    @Override
    public int hashCode() {
      return uniqueId.hashCode();
    }
  }
}
