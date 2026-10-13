package dev.vexsoft.core.common.service.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDataSnapshot;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.redis.RedisPlayerDataStore;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/** Coordinates player-data definitions, online containers, asynchronous saves, and recovery snapshots. */
@Dependencies({PlayerDataStoreService.class, CacheService.class, ProfileLeaseService.class})
public final class VexPlayerDataCoordinatorService implements PlayerDataCoordinatorService, AutoCloseable {

    private final Map<UUID, VexPlayer> players = new ConcurrentHashMap<>();
    private final Map<VexPlayer, Map<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>>> unloadedSnapshots =
        Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
    private final Map<UUID, SaveRequest> pendingSaves = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> retirements = new HashMap<>();
    private final Set<UUID> removalRequested = new HashSet<>();
    private final ExecutorService saveExecutor = new ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1024),
        Thread.ofPlatform().name("vex-player-snapshot-", 0).daemon().factory(),
        new ThreadPoolExecutor.AbortPolicy()
    );

    private volatile CompletableFuture<Void> shutdownDrain = CompletableFuture.completedFuture(null);

    private final Map<String, OwnerContainers> containersByOwner = new LinkedHashMap<>();
    private final Map<Class<? extends PlayerContainer>, RegisteredContainer<?>> featureContainers =
        new LinkedHashMap<>();
    private volatile Map<Class<? extends PlayerContainer>, Integer> featureContainerSlotSnapshot = Map.of();
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
    private final ProfileLeaseService leases;
    private volatile CompletableFuture<Void> readiness;
    private final Map<PlayerProfileContext, Map<ProfileDocumentKey, Long>> profileRevisions = new ConcurrentHashMap<>();
    private final Map<PlayerProfileContext, List<ProfileDocumentChange>> profileWriteGuards = new ConcurrentHashMap<>();
    private volatile BiFunction<VexPlayer, PlayerProfileContext, CompletableFuture<Void>> platformCapture;
    private final VexAsyncCache<PlayerLoadRequest, VexPlayer> playerLoads;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public VexPlayerDataCoordinatorService(final VexServiceRegistry services) {
        VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");

        PlayerDataStoreService storage = checkedServices.require(PlayerDataStoreService.class);
        store = storage.getStore();
        leases = checkedServices.find(ProfileLeaseService.class).orElse(null);
        readiness = storage.getReadiness();
        if (store instanceof ProfileDocumentStore profiles) {
            readiness = readiness.thenCompose(ignored -> profiles.reconcileProfileDocuments());
        }
        playerLoads = checkedServices.require(CacheService.class).createAsync(
            "player-loads",
            VexCacheOptions.builder().maximumSize(1_000L).expireAfterWrite(Duration.ofMinutes(1)).build(),
            this::loadFromStore
        );
    }

    @Override
    public void prepareUnload(final ServiceOwner owner) {
        Collection<DataContainerKey<?>> keys = getKeys(owner);

        for (VexPlayer player : List.copyOf(players.values())) {
            synchronized (player) {
                var frozen = unloadedSnapshots.computeIfAbsent(player, ignored -> new LinkedHashMap<>());

                for (DataContainerKey<?> key : keys) {
                    if (!player.has(key)) {
                        continue;
                    }
                    frozen.put(key, player.recoverySnapshot(key, objectMapper::valueToTree));
                }
            }
        }
    }

    @Override
    public void drainBeforeClose(final CompletableFuture<Void> drain) {
        shutdownDrain = Objects.requireNonNull(drain, "drain");
    }

    @Override
    public void close() {
        shutdownDrain.handle((ignored, failure) -> null).thenRun(saveExecutor::shutdown);
    }

    @Override
    public void exportRecovery(final Path directory) {
        Path target = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Map<String, OwnerContainers> owners;

        synchronized (this) {
            owners = ownerSnapshot();
        }

        try {
            Files.createDirectories(target);

            for (VexPlayer player : List.copyOf(players.values())) {
                var document = objectMapper.createObjectNode();

                synchronized (player) {
                    var dirty = player.getDirtyKeys();

                    if (dirty.isEmpty()) {
                        continue;
                    }

                    document.put("playerId", player.getUniqueId().toString());
                    document.put("name", player.getName());
                    document.put("capturedAt", Instant.now().toString());
                    document.set("profileContexts", objectMapper.valueToTree(player.profileContexts().values()));
                    var data = document.putObject("owners");

                    owners.forEach((ownerName, owner) -> {
                        var ownerData = data.putObject(ownerName);

                        for (DataContainerKey<?> key : owner.keys.values()) {
                            if (player.has(key)) {
                                ownerData.set(key.getName(), snapshotData(player, key).getValue());
                                document.withObject("/addresses").withObject("/" + ownerName)
                                    .set(key.getName(), objectMapper.valueToTree(player.addressOf(key)));
                            }
                        }
                    });
                }

                Path file = target.resolve(player.getUniqueId() + ".json");
                Path temporary = target.resolve(player.getUniqueId() + ".json.tmp");

                Files.writeString(
                    temporary,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document)
                );

                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to export player recovery data to " + target, failure);
        }
    }

    @Override
    public synchronized void register(final ServiceOwner owner, final PlayerDataDefinition definition) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(definition, "definition");
        String ownerName = normalizeOwner(owner.getServiceOwnerName());
        OwnerContainers ownerContainers =
            containersByOwner.computeIfAbsent(ownerName, ignored -> new OwnerContainers(owner));

        if (ownerContainers.owner != owner) {
            throw new IllegalStateException("Player data owner name is already registered: " + ownerName);
        }

        List<DataContainerKey<?>> added = new ArrayList<>();
        DataContainerRegistry registry = key -> {
            Objects.requireNonNull(key, "key");
            DataContainerKey<?> existing = ownerContainers.keys.putIfAbsent(key.getName(), key);

            if (existing != null && existing != key) {
                throw new IllegalStateException(
                    "Player container is already registered: " + ownerName + ":" + key.getName());
            }

            if (existing == null) {
                added.add(key);
            }
        };

        try {
            definition.register(registry);
            List<DataContainerKey<?>> accountKeys = ownerContainers.keys.values().stream()
                .filter(key -> key.getScope().domain().isEmpty()).toList();
            // Logins await this chain; registration itself never performs database work on the server thread.
            readiness = readiness.thenCompose(ignored -> store.reconcile(ownerName, accountKeys));
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
        PlayerContainerFactory<? extends T> checkedFactory = Objects.requireNonNull(factory, "factory");

        if (featureContainers.containsKey(checkedType)) {
            throw new IllegalStateException("Player feature container is already registered: " + checkedType.getName());
        }

        RegisteredContainer<T> registered =
            new RegisteredContainer<>(owner, checkedType, checkedFactory, nextFeatureContainerSlot++);

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
        List<RegisteredContainer<?>> removed =
            featureContainers.values().stream().filter(container -> container.owner == owner).toList();

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
        VexPlayer player =
            players.computeIfAbsent(uniqueId, ignored -> new VexPlayer(uniqueId, name, this::findContainerSlot));

        player.setName(name);
        if (leases != null) {
            player.setProfileAccessGuard(leases::isWritable);
        }

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

        synchronized (saveLock) {
            CompletableFuture<Void> retirement = retirements.get(uniqueId);

            if (retirement != null) {
                return retirement.thenCompose(ignored -> load(uniqueId, name));
            }

            VexPlayer existing = players.get(uniqueId);

            if (existing != null) {
                removalRequested.remove(uniqueId);
                existing.setName(name);

                return CompletableFuture.completedFuture(existing);
            }

            if (players.size() >= 1024) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                    "Player data capacity reached; waiting for pending saves to recover"));
            }
        }

        CompletableFuture<Void> previousSave;

        synchronized (saveLock) {
            previousSave = saveChains.get(uniqueId);
        }

        if (previousSave != null && !previousSave.isDone()) {
            return previousSave.thenCompose(ignored -> load(uniqueId, name));
        }

        PlayerLoadRequest request = new PlayerLoadRequest(uniqueId, name);

        return readiness.thenCompose(ignored -> playerLoads.get(request)).thenApply(player -> {
            player.setName(name);

            return player;
        }).whenComplete((player, throwable) -> playerLoads.invalidate(request));
    }

    private CompletableFuture<VexPlayer> loadFromStore(final PlayerLoadRequest request) {
        VexPlayer existing = players.get(request.getUniqueId());

        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        Map<String, OwnerContainers> owners;

        synchronized (this) {
            owners = ownerSnapshot();
        }

        Map<DataContainerKey<?>, Object> loaded = new ConcurrentHashMap<>();
        CompletableFuture<?>[] loads = owners.entrySet()
            .stream()
            .map(entry -> store.load(entry.getKey(), request.getUniqueId(), entry.getValue().keys.values().stream()
                    .filter(key -> key.getScope().domain().isEmpty()).toList())
                .thenAccept(values -> readOwnerValues(entry.getValue(), values, loaded)))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(loads).thenApply(ignored -> {
            VexPlayer player = new VexPlayer(request.getUniqueId(), request.getName(), this::findContainerSlot);
            if (leases != null) {
                player.setProfileAccessGuard(leases::isWritable);
            }

            for (OwnerContainers owner : owners.values()) {
                for (DataContainerKey<?> key : owner.keys.values()) {
                    if (key.getScope().domain().isPresent()) {
                        continue;
                    }
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
    public CompletableFuture<ProfileDataSnapshot> loadProfile(final PlayerProfileContext context) {
        Objects.requireNonNull(context, "context");
        if (!(store instanceof ProfileDocumentStore profiles)) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Profile storage unavailable"));
        }
        Map<ProfileDocumentKey, DataContainerKey<?>> keys = new LinkedHashMap<>();
        ownerSnapshot().forEach((ownerName, owner) -> owner.keys.values().forEach(key -> {
            if (key.getScope().domain().filter(context.domain()::equals).isPresent()) {
                keys.put(new ProfileDocumentKey(context.address(), ownerName, key.getName()), key);
            }
        }));
        return readiness.thenCompose(ignored -> profiles.loadProfileDocuments(keys.keySet())).thenApply(stored -> {
            Map<DataContainerKey<?>, Object> values = new LinkedHashMap<>();
            Map<ProfileDocumentKey, Long> revisions = new LinkedHashMap<>();
            keys.forEach((document, key) -> {
                StoredProfileDocument value = stored.get(document);
                try {
                    values.put(key, value == null ? key.createDefaultValue()
                        : objectMapper.readValue(value.value(), key.getType()));
                } catch (JsonProcessingException failure) {
                    throw new IllegalStateException("Invalid profile data: " + document, failure);
                }
                revisions.put(document, value == null ? 0L : value.revision());
            });
            return new ProfileDataSnapshot(context, values, revisions);
        });
    }

    @Override
    public List<ProfileDocumentChange> profileChanges(
        final ProfileDataSnapshot snapshot, final Map<DataContainerKey<?>, Object> replacements
    ) {
        List<ProfileDocumentChange> changes = new ArrayList<>();
        ownerSnapshot().forEach((ownerName, owner) -> owner.keys.values().forEach(key -> {
            if (replacements.containsKey(key)) {
                ProfileDocumentKey document = new ProfileDocumentKey(snapshot.context().address(), ownerName,
                    key.getName());
                Long revision = snapshot.revisions().get(document);
                Object value = replacements.get(key);
                if (revision == null || !key.getType().isInstance(value)) {
                    throw new IllegalArgumentException("Replacement is not part of the addressed snapshot");
                }
                try {
                    changes.add(new ProfileDocumentChange(document, revision, objectMapper.writeValueAsString(value)));
                } catch (JsonProcessingException failure) {
                    throw new IllegalStateException("Unable to serialize profile replacement", failure);
                }
            }
        }));
        if (changes.size() != replacements.size()) {
            throw new IllegalArgumentException("Unknown profile replacement key");
        }
        return List.copyOf(changes);
    }

    @Override
    public CompletableFuture<ProfileDataSnapshot> snapshotProfile(
        final VexPlayer player, final PlayerProfileContext context
    ) {
        if (!store.isLive()) {
            return loadProfile(context);
        }
        // A completed live baseline can call this directly from the entity thread during a shop click.
        return CompletableFuture.supplyAsync(() -> {
            Map<DataContainerKey<?>, JsonNode> captured = new LinkedHashMap<>();
            Map<String, OwnerContainers> owners = ownerSnapshot();
            Map<ProfileDocumentKey, Long> revisions;
            synchronized (player) {
                if (!player.isCurrentContext(context)) {
                    throw new IllegalStateException("Profile context retired");
                }
                owners.values().forEach(owner -> owner.keys.values().forEach(key -> {
                    if (key.getScope().domain().filter(context.domain()::equals).isPresent()) {
                        captured.put(key, snapshotData(player, key).getValue());
                    }
                }));
                revisions = Map.copyOf(profileRevisions.getOrDefault(context, Map.of()));
            }
            Map<DataContainerKey<?>, Object> values = new LinkedHashMap<>();
            captured.forEach((key, value) -> values.put(key, objectMapper.convertValue(value, key.getType())));
            return new ProfileDataSnapshot(context, values, revisions);
        }, saveExecutor);
    }

    @Override
    public void installProfile(final VexPlayer player, final ProfileDataSnapshot snapshot) {
        installProfile(player, snapshot, List.of());
    }

    @Override
    public void installProfile(
        final VexPlayer player, final ProfileDataSnapshot snapshot, final List<ProfileDocumentChange> guards
    ) {
        synchronized (player) {
            PlayerProfileContext previous = player.findProfileContext(snapshot.context().domain()).orElse(null);
            player.installProfile(snapshot.context(), snapshot.values());
            profileRevisions.put(snapshot.context(), new ConcurrentHashMap<>(snapshot.revisions()));
            profileWriteGuards.put(snapshot.context(), List.copyOf(guards));
            if (previous != null && !previous.equals(snapshot.context())) {
                profileRevisions.remove(previous);
                profileWriteGuards.remove(previous);
            }
        }
    }

    @Override
    public Collection<VexPlayer> getLoadedPlayers() {
        return List.copyOf(players.values());
    }

    @Override
    public Optional<VexPlayer> remove(final UUID uniqueId) {
        VexPlayer removed = players.remove(Objects.requireNonNull(uniqueId, "uniqueId"));

        if (removed != null) {
            store.releasePlayer(uniqueId);
            removed.profileContexts().values().forEach(context -> {
                profileRevisions.remove(context);
                profileWriteGuards.remove(context);
            });
            removed.closeContainers();
        }

        return Optional.ofNullable(removed);
    }

    @Override
    public CompletableFuture<Void> saveAndRemove(final UUID uniqueId) {
        synchronized (saveLock) {
            CompletableFuture<Void> existing = retirements.get(uniqueId);

            if (existing != null) {
                return existing;
            }

            VexPlayer player = players.get(Objects.requireNonNull(uniqueId, "uniqueId"));

            if (player == null) {
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<Void> retirement = new CompletableFuture<>();

            removalRequested.add(uniqueId);
            retirements.put(uniqueId, retirement);
            queueSave(player).whenComplete((ignored, throwable) -> {
                synchronized (saveLock) {
                    try {
                        // Failed saves retain their dirty session for retry instead of losing the only
                        // copy.
                        if (throwable == null) {
                            removalRequested.remove(uniqueId);

                            if (players.remove(uniqueId, player)) {
                                store.releasePlayer(uniqueId);
                                player.profileContexts().values().forEach(context -> {
                                    profileRevisions.remove(context);
                                    profileWriteGuards.remove(context);
                                });
                                player.closeContainers();
                            }
                        }
                    } catch (RuntimeException failure) {
                        retirements.remove(uniqueId, retirement);
                        retirement.completeExceptionally(failure);

                        return;
                    }

                    retirements.remove(uniqueId, retirement);

                    if (throwable == null) {
                        retirement.complete(null);
                    } else {
                        retirement.completeExceptionally(throwable);
                    }
                }
            });

            return retirement;
        }
    }

    @Override
    public synchronized AutoCloseable onCapture(
        final BiFunction<VexPlayer, PlayerProfileContext, CompletableFuture<Void>> capture
    ) {
        if (platformCapture != null) {
            throw new IllegalStateException("Platform capture already registered");
        }
        platformCapture = Objects.requireNonNull(capture, "capture");
        return () -> platformCapture = null;
    }

    @Override
    public CompletableFuture<Void> saveCaptured(final UUID playerId, final PlayerProfileContext expected) {
        VexPlayer player = players.get(playerId);
        var capture = platformCapture;
        if (player != null && capture != null && !player.profileContexts().isEmpty()
            && player.findPlatformPlayer(Object.class).isPresent()) {
            return capture.apply(player, expected);
        }
        return expected == null ? save(playerId) : save(expected);
    }

    @Override
    public CompletableFuture<Void> save(final UUID uniqueId) {
        VexPlayer player = players.get(Objects.requireNonNull(uniqueId, "uniqueId"));

        return player == null ? CompletableFuture.completedFuture(null) : queueSave(player);
    }

    @Override
    public CompletableFuture<Void> saveCaptured(final UUID uniqueId) {
        VexPlayer player = players.get(Objects.requireNonNull(uniqueId, "uniqueId"));
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (player) {
            var snapshots = new LinkedHashMap<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>>();
            for (DataContainerKey<?> key : player.getDirtyKeys()) {
                snapshots.put(key, snapshotData(player, key));
            }
            return queueSave(player, List.of(), Map.copyOf(snapshots));
        }
    }

    @Override
    public CompletableFuture<Void> save(final UUID uniqueId, final List<ProfileDocumentChange> changes) {
        VexPlayer player = players.get(uniqueId);
        if (player == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Profile session is not loaded"));
        }
        return queueSave(player, List.copyOf(changes));
    }

    @Override
    public CompletableFuture<Void> save(final PlayerProfileContext context) {
        VexPlayer player = players.get(context.playerId());
        if (player == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Captured session is not loaded"));
        }
        synchronized (player) {
            if (!player.isCurrentContext(context)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Captured profile session retired"));
            }
            return queueSave(player);
        }
    }

    @Override
    public <T> CompletableFuture<Optional<T>> readStored(
        final ServiceOwner owner, final PlayerProfileContext context, final DataContainerKey<T> key
    ) {
        if (getKeys(owner).stream().noneMatch(registered -> registered == key)
            || !key.getScope().domain().filter(context.domain()::equals).isPresent()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unregistered or mismatched profile key"));
        }
        ProfileDocumentKey document = new ProfileDocumentKey(context.address(),
            normalizeOwner(owner.getServiceOwnerName()), key.getName());
        return readiness.thenCompose(ignored -> ((ProfileDocumentStore) store).loadProfileDocuments(List.of(document)))
            .thenApply(values -> {
                StoredProfileDocument stored = values.get(document);
                if (stored == null) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(objectMapper.readValue(stored.value(), key.getType()));
                } catch (JsonProcessingException failure) {
                    throw new IllegalStateException("Invalid stored profile value: " + key.getName(), failure);
                }
            });
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        CompletableFuture<?>[] saves = players.keySet()
            .stream()
            .map(players::get)
            .filter(Objects::nonNull)
            .map(this::queueSave)
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(saves);
    }

    @Override
    public <T> CompletableFuture<Optional<T>> readStored(
        final ServiceOwner owner,
        final UUID playerId,
        final DataContainerKey<T> key
    ) {
        if (key.getScope().domain().isPresent()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                "Profile reads require an explicit profile address"));
        }
        if (getKeys(owner).stream().noneMatch(registered -> registered == key)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unregistered owner container"));
        }
        String ownerName = normalizeOwner(owner.getServiceOwnerName());
        return store.readStored(ownerName, Objects.requireNonNull(playerId, "playerId"), List.of(key)).thenApply(values -> {
            String json = values.get(key.getName());
            if (json == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(json, key.getType()));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("Unable to read persisted player container: " + key.getName(), failure);
            }
        });
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
        return containersByOwner.values()
            .stream()
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
    public CompletableFuture<Void> resetPlayerContainer(final UUID uniqueId, final String containerId) {
        UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        ContainerSelection selection = requireContainer(containerId);
        if (!selection.key.getScope().isAccount()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("An explicit profile address is required"));
        }
        VexPlayer loaded = players.get(checkedUniqueId);

        if (loaded != null) {
            loaded.reset(selection.key);
        }

        CompletableFuture<?> reset = store.reset(selection.owner, checkedUniqueId, List.of(selection.key.getName()));

        return loaded == null ? reset.thenApply(ignored -> null) : reset.thenCompose(ignored -> queueSave(loaded));
    }

    @Override
    public CompletableFuture<Void> resetPlayerContainers(final UUID uniqueId) {
        UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        Map<String, OwnerContainers> owners = ownerSnapshot();
        VexPlayer loaded = players.get(checkedUniqueId);

        if (loaded != null) {
            resetPlayer(loaded, owners);
        }

        CompletableFuture<?>[] resets = owners.entrySet()
            .stream()
            .map(entry -> store.reset(entry.getKey(), checkedUniqueId, accountKeys(entry.getValue())))
            .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> stored = CompletableFuture.allOf(resets);

        return loaded == null ? stored : stored.thenCompose(ignored -> queueSave(loaded));
    }

    @Override
    public CompletableFuture<Void> resetGlobalContainer(final String containerId) {
        ContainerSelection selection = requireContainer(containerId);
        if (!selection.key.getScope().isAccount()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("An explicit profile address is required"));
        }
        List<VexPlayer> loaded = List.copyOf(players.values());

        for (VexPlayer player : loaded) {
            player.reset(selection.key);
        }

        CompletableFuture<?> reset = store.resetAll(selection.owner, List.of(selection.key.getName()));

        return reset.thenCompose(ignored -> savePlayers(loaded));
    }

    @Override
    public CompletableFuture<Void> resetGlobalContainers() {
        Map<String, OwnerContainers> owners = ownerSnapshot();
        List<VexPlayer> loaded = List.copyOf(players.values());

        for (VexPlayer player : loaded) {
            resetPlayer(player, owners);
        }

        CompletableFuture<?>[] resets = owners.entrySet()
            .stream()
            .map(entry -> store.resetAll(entry.getKey(), accountKeys(entry.getValue())))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(resets).thenCompose(ignored -> savePlayers(loaded));
    }

    @Override
    public CompletableFuture<Optional<UUID>> resolveUniqueId(final String player) {
        String checkedPlayer = Objects.requireNonNull(player, "player").trim();

        try {
            return CompletableFuture.completedFuture(Optional.of(UUID.fromString(checkedPlayer)));
        } catch (IllegalArgumentException ignored) {
            Optional<UUID> loaded = players.values()
                .stream()
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
            result = result.thenCompose(found -> found.isPresent() ? CompletableFuture.completedFuture(found)
                : store.findUniqueId(owner, checkedPlayer));
        }

        return result;
    }

    /** Called under the player lock, including recovery, so frozen data cannot race a save. */
    private VexPlayer.ContainerSnapshot<JsonNode> snapshotData(final VexPlayer player, final DataContainerKey<?> key) {
        var frozen = unloadedSnapshots.get(player);
        var snapshot = frozen == null ? null : frozen.get(key);

        if (snapshot == null) {
            return player.recoverySnapshot(key, objectMapper::valueToTree);
        }

        if (player.recoverySnapshot(key, ignored -> null).getRevision() != snapshot.getRevision()) {
            throw new IllegalStateException("Player data changed after owner unload: " + key.getName());
        }

        return snapshot;
    }

    private synchronized ContainerSelection requireContainer(final String containerId) {
        String checkedName = Objects.requireNonNull(containerId, "containerId").trim().toLowerCase(Locale.ROOT);
        ContainerSelection found = null;

        for (Map.Entry<String, OwnerContainers> entry : containersByOwner.entrySet()) {
            DataContainerKey<?> key = entry.getValue().keys.get(checkedName);

            if (key == null) {
                continue;
            }

            if (found != null) {
                throw new IllegalArgumentException("Player data container name is ambiguous: " + checkedName);
            }

            found = new ContainerSelection(entry.getKey(), key);
        }

        if (found == null) {
            throw new IllegalArgumentException("Player data container is not registered: " + checkedName);
        }

        return found;
    }

    private synchronized Map<String, OwnerContainers> ownerSnapshot() {
        Map<String, OwnerContainers> snapshot = new LinkedHashMap<>();
        containersByOwner.forEach((name, owner) -> {
            OwnerContainers copy = new OwnerContainers(owner.owner);
            copy.keys.putAll(owner.keys);
            snapshot.put(name, copy);
        });
        return snapshot;
    }

    private static List<String> accountKeys(final OwnerContainers owner) {
        return owner.keys.values().stream().filter(key -> key.getScope().isAccount())
            .map(DataContainerKey::getName).toList();
    }

    private static void resetPlayer(final VexPlayer player, final Map<String, OwnerContainers> owners) {
        for (OwnerContainers owner : owners.values()) {
            for (DataContainerKey<?> key : owner.keys.values()) {
                if (key.getScope().isAccount()) {
                    player.reset(key);
                }
            }
        }
    }

    private CompletableFuture<Void> savePlayers(final Collection<VexPlayer> loaded) {
        CompletableFuture<?>[] saves = loaded.stream().map(this::queueSave).toArray(CompletableFuture[]::new);

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

    private SerializedOwner serializeOwner(
        final OwnerContainers owner,
        final Map<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>> snapshots
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<DataContainerKey<?>, Long> revisions = new LinkedHashMap<>();

        for (DataContainerKey<?> key : owner.keys.values()) {
            var snapshot = snapshots.get(key);

            if (snapshot == null) {
                continue;
            }

            try {
                values.put(key.getName(), objectMapper.writeValueAsString(snapshot.getValue()));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to write player container " + key.getName(), exception);
            }

            revisions.put(key, snapshot.getRevision());
        }

        return new SerializedOwner(Map.copyOf(values), Map.copyOf(revisions));
    }

    /** Serialized container values and the revisions captured for one owner's save. */
    public record SerializedOwner(Map<String, String> values, Map<DataContainerKey<?>, Long> revisions) {

        /** Freezes the captured owner values and revisions independently of the caller. */
        public SerializedOwner {
            values = Map.copyOf(values);
            revisions = Map.copyOf(revisions);
        }
    }

    private CompletableFuture<Void> saveNow(
        final VexPlayer player,
        final Map<ProfileDomain, PlayerProfileContext> contexts,
        final List<ProfileDocumentChange> additional,
        final Map<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>> captured
    ) {
        Map<String, OwnerContainers> owners;

        synchronized (this) {
            owners = ownerSnapshot();
        }

        Map<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>> snapshots = new LinkedHashMap<>();

        synchronized (player) {
            if (!player.profileContexts().equals(contexts)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Captured profile session retired"));
            }
            if (captured == null) {
                for (DataContainerKey<?> key : player.getDirtyKeys()) {
                    snapshots.put(key, snapshotData(player, key));
                }
            } else {
                snapshots.putAll(captured);
            }
        }

        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        Map<DataContainerKey<?>, Long> revisions = new LinkedHashMap<>();
        List<ProfileDocumentChange> profileChanges = new ArrayList<>(additional);

        owners.forEach((name, owner) -> {
            SerializedOwner serialized = serializeOwner(owner, snapshots);

            Map<String, String> accountValues = new LinkedHashMap<>();
            serialized.values().forEach((keyName, json) -> {
                DataContainerKey<?> key = owner.keys.get(keyName);
                ProfileDomain domain = key.getScope().domain().orElse(null);
                if (domain == null) {
                    accountValues.put(keyName, json);
                    return;
                }
                PlayerProfileContext context = contexts.get(domain);
                ProfileDocumentKey document = new ProfileDocumentKey(context.address(), name, keyName);
                Long expected = profileRevisions.getOrDefault(context, Map.of()).get(document);
                if (expected == null) {
                    throw new IllegalStateException("Profile document was not loaded: " + document);
                }
                profileChanges.add(new ProfileDocumentChange(document, expected, json));
            });
            if (!accountValues.isEmpty()) {
                values.put(name, accountValues);
            }

            revisions.putAll(serialized.revisions());
        });

        CompletableFuture<Void> saved = store.isLive() ? CompletableFuture.completedFuture(null)
            : store.saveAllOwners(player.getUniqueId(), player.getName(), values);
        if (!profileChanges.isEmpty()) {
            if (leases != null) {
                contexts.values().forEach(context -> profileChanges.add(leases.writeGuard(context)));
            }
            contexts.values().forEach(context -> profileChanges.addAll(profileWriteGuards.getOrDefault(context, List.of())));
            saved = saved.thenCompose(ignored -> (store instanceof RedisPlayerDataStore live
                ? live.commitPlayer(player.getUniqueId(), values, profileChanges)
                : ((ProfileDocumentStore) store).commitProfileDocuments(profileChanges)).thenAccept(committed -> {
                    if (!committed) {
                        throw new IllegalStateException("Profile save revision conflict; refusing to overwrite");
                    }
                    for (PlayerProfileContext context : contexts.values()) {
                        Map<ProfileDocumentKey, Long> durable = profileRevisions.get(context);
                        if (durable != null) {
                            profileChanges.stream().filter(change -> change.value() != null
                                    && durable.containsKey(change.key())
                                    && change.key().address().equals(context.address()))
                                .forEach(change -> durable.put(change.key(), change.expectedRevision() + 1));
                        }
                    }
                }));
        } else if (store.isLive()) {
            saved = store.saveAllOwners(player.getUniqueId(), player.getName(), values);
        }
        return saved.thenRun(() -> revisions.forEach((key, revision) -> player.markClean(
            key, key.getScope().domain().map(contexts::get).orElse(null), revision)));
    }

    private CompletableFuture<Void> queueSave(final VexPlayer player) {
        return queueSave(player, List.of());
    }

    private CompletableFuture<Void> queueSave(final VexPlayer player, final List<ProfileDocumentChange> additional) {
        return queueSave(player, additional, null);
    }

    private CompletableFuture<Void> queueSave(
        final VexPlayer player,
        final List<ProfileDocumentChange> additional,
        final Map<DataContainerKey<?>, VexPlayer.ContainerSnapshot<JsonNode>> captured
    ) {
        UUID uniqueId = player.getUniqueId();
        Map<ProfileDomain, PlayerProfileContext> contexts = player.profileContexts();
        CompletableFuture<Void> next;
        SaveRequest request = new SaveRequest(contexts, additional.isEmpty() && captured == null);

        synchronized (saveLock) {
            SaveRequest pending = pendingSaves.get(uniqueId);
            if (request.coalescible && pending != null && pending.coalescible && pending.contexts.equals(contexts)) {
                return pending.future;
            }
            CompletableFuture<Void> previous = saveChains.get(uniqueId);
            CompletableFuture<Void> ready = previous == null ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, throwable) -> null);

            next = ready.thenComposeAsync(
                ignored -> {
                    synchronized (saveLock) {
                        pendingSaves.remove(uniqueId, request);
                    }

                    return readiness.thenCompose(unused -> saveNow(player, contexts, additional, captured));
                },
                saveExecutor
            );
            saveChains.put(uniqueId, next);
            request.future = next;
            pendingSaves.put(uniqueId, request);
        }

        CompletableFuture<Void> queued = next;

        queued.whenComplete((ignored, throwable) -> {
            synchronized (saveLock) {
                saveChains.remove(uniqueId, queued);
                pendingSaves.remove(uniqueId, request);

                if (throwable == null && !retirements.containsKey(uniqueId) && removalRequested.remove(uniqueId)) {
                    if (players.remove(uniqueId, player)) {
                        store.releasePlayer(uniqueId);
                        player.closeContainers();
                    }
                }
            }
        });

        return queued;
    }

    private static String normalizeOwner(final String ownerName) {
        String normalized =
            Objects.requireNonNull(ownerName, "ownerName").trim().toLowerCase(Locale.ROOT).replace('-', '_');

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
    private static void installContainerUnchecked(final VexPlayer player, final RegisteredContainer<?> registered) {
        installContainer(player, (RegisteredContainer<PlayerContainer>) registered);
    }

    private static <T> void installDefault(final VexPlayer player, final DataContainerKey<T> key, final boolean dirty) {
        if (key.getScope().domain().filter(domain -> player.findProfileContext(domain).isEmpty()).isPresent()) {
            return;
        }
        if (!player.has(key)) {
            player.install(key, key.createDefaultValue(), dirty);
        }
    }

    private static <T> void installLoaded(final VexPlayer player, final DataContainerKey<T> key, final Object value) {
        player.install(key, key.getType().cast(value), false);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class OwnerContainers {

        private final ServiceOwner owner;
        private final Map<String, DataContainerKey<?>> keys = new LinkedHashMap<>();

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class RegisteredContainer<T extends PlayerContainer> {

        private final ServiceOwner owner;
        private final Class<T> type;
        private final PlayerContainerFactory<? extends T> factory;
        private final int slot;

    }

    private record ContainerSelection(String owner, DataContainerKey<?> key) {

    }

    private static final class SaveRequest {

        private final Map<ProfileDomain, PlayerProfileContext> contexts;
        private CompletableFuture<Void> future;
        private final boolean coalescible;

        private SaveRequest(final Map<ProfileDomain, PlayerProfileContext> contexts, final boolean coalescible) {
            this.contexts = contexts;
            this.coalescible = coalescible;
        }
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
