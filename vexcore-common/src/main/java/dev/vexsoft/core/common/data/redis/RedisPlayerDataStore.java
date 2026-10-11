package dev.vexsoft.core.common.data.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.global.GlobalDataReference;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.global.StoredGlobalData;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XTrimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared authoritative state with atomic revision checks, retry receipts and complete SQL recovery checkpoints. */
public final class RedisPlayerDataStore
    implements PlayerDataStore, GlobalDataStore, PlayerIdentityStore, ProfileDocumentStore {

    private static final String COMMIT = resource("commit.lua");
    private static final String READ = resource("read.lua");
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> CHANGE_MAP = new TypeReference<>() {
    };
    private static final String UNLOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then"
        + " return redis.call('DEL', KEYS[1]) end return 0";
    private static final String BOOTSTRAP = "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end "
        + "if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end "
        + "redis.call('RENAME', KEYS[3], KEYS[4]); redis.call('PERSIST', KEYS[4]); "
        + "redis.call('DEL', KEYS[5], KEYS[6], KEYS[7]); "
        + "if ARGV[3] ~= '0-0' then redis.call('XADD', KEYS[5], ARGV[3], 'changes', '{}') end "
        + "redis.call('SET', KEYS[2], ARGV[2]); return 1";

    private final PlayerDataStore durable;
    private final JdbcNetworkCheckpoints checkpoints;
    private final RedisSettings settings;
    private final RedisClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Semaphore pending;
    private final Logger logger = Logger.getLogger(RedisPlayerDataStore.class.getName());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("VexCore-Redis-checkpoints").factory());
    private final CopyOnWriteArrayList<Consumer<GlobalDataReference>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean checkpointRunning = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean checkpointRequested = new AtomicBoolean();
    private final Map<UUID, Map<String, Long>> accountRevisions = new ConcurrentHashMap<>();
    private final Set<GlobalDataReference> observedGlobals = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> checkpointFuture = CompletableFuture.completedFuture(null);
    private final CompletableFuture<Void> initialization;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile RedisAsyncCommands<String, String> commands;
    private volatile String epoch;
    private volatile String observedPosition;
    private volatile Throwable fencingFailure;

    public RedisPlayerDataStore(final PlayerDataStore durable, final RedisSettings settings) {
        this.durable = Objects.requireNonNull(durable, "durable");
        this.settings = Objects.requireNonNull(settings, "settings");
        checkpoints = durable.networkCheckpoints();
        pending = new Semaphore(settings.maximumPendingCommands());
        client = RedisClient.create(settings.uri());
        client.setOptions(ClientOptions.builder().requestQueueSize(settings.maximumPendingCommands())
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .socketOptions(SocketOptions.builder().connectTimeout(settings.connectTimeout()).build()).build());
        initialization = durable.readiness().thenRunAsync(this::initialize, worker);
        worker.scheduleWithFixedDelay(() -> {
            if (initialization.isDone() && !initialization.isCompletedExceptionally() && !closed.get()) {
                scheduledCheckpoint().exceptionally(failure -> {
                    logger.log(Level.SEVERE, "Redis network checkpoint failed; the previous checkpoint remains complete", failure);
                    return null;
                });
            }
        }, settings.checkpointIntervalSeconds(), settings.checkpointIntervalSeconds(), TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(this::pollInvalidations, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public CompletableFuture<Void> readiness() {
        if (fencingFailure != null) {
            return CompletableFuture.failedFuture(fencingFailure);
        }
        return initialization.copy();
    }

    /** Supplies the managed asynchronous connection to the platform's network-message channel. */
    public CompletableFuture<RedisAsyncCommands<String, String>> messageCommands() {
        return readiness().thenApply(ignored -> commands);
    }

    /** Returns the epoch already verified by readiness; callers use it inside their own atomic Redis scripts. */
    public String liveEpoch() {
        return epoch;
    }

    /** Disables this instance after a duplicate node or lost network ownership is detected. */
    public void fence(final Throwable failure) {
        fencingFailure = failure;
    }

    /** Returns this network's key namespace for ephemeral coordination resources. */
    public String namespace() {
        return settings.prefix() + "{network}:";
    }

    @Override
    public CompletableFuture<Void> reconcile(final String owner, final Collection<DataContainerKey<?>> keys) {
        return readiness();
    }

    @Override
    public CompletableFuture<Void> reconcileProfileDocuments() {
        return readiness();
    }

    @Override
    public CompletableFuture<Void> reconcileGlobalData() {
        return readiness();
    }

    @Override
    public CompletableFuture<Void> reconcilePlayerIdentities() {
        return readiness();
    }

    @Override
    public CompletableFuture<Map<String, String>> load(
        final String owner, final UUID playerId, final Collection<DataContainerKey<?>> keys
    ) {
        return loadAccounts(owner, playerId, keys, true);
    }

    @Override
    public CompletableFuture<Map<String, String>> readStored(
        final String owner, final UUID playerId, final Collection<DataContainerKey<?>> keys
    ) {
        return loadAccounts(owner, playerId, keys, false);
    }

    @Override
    public void releasePlayer(final UUID playerId) {
        accountRevisions.remove(playerId);
    }

    @Override
    public CompletableFuture<Void> save(
        final String owner, final UUID playerId, final String name, final Map<String, String> values
    ) {
        return saveAllOwners(playerId, name, Map.of(owner, values));
    }

    @Override
    public CompletableFuture<Void> saveAllOwners(
        final UUID playerId, final String name, final Map<String, Map<String, String>> owners
    ) {
        return commitPlayer(playerId, owners, List.of()).thenAccept(committed -> {
            if (!committed) {
                throw new IllegalStateException("Account save conflicted");
            }
        });
    }

    /** Saves account containers and guarded profile changes in the same Redis operation. */
    public CompletableFuture<Boolean> commitPlayer(
        final UUID playerId, final Map<String, Map<String, String>> owners,
        final Collection<ProfileDocumentChange> profileChanges
    ) {
        List<Change> changes = profileChanges(profileChanges);
        Map<String, Long> revisions = accountRevisions.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        owners.forEach((owner, values) -> values.forEach((key, value) -> {
            String field = account(owner, playerId, key);
            changes.add(new Change(field, revisions.getOrDefault(field, 0L), value, 0, false));
        }));
        return commit(changes).thenApply(committed -> {
            if (committed) {
                changes.stream().filter(change -> change.key().startsWith("a|"))
                    .forEach(change -> revisions.put(change.key(), change.expected() + 1));
            }
            return committed;
        });
    }

    @Override
    public CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> loadProfileDocuments(
        final Collection<ProfileDocumentKey> keys
    ) {
        List<ProfileDocumentKey> captured = List.copyOf(keys);
        return read(captured.stream().map(RedisPlayerDataStore::profile).toList()).thenApply(values -> {
            Map<ProfileDocumentKey, StoredProfileDocument> result = new LinkedHashMap<>();
            captured.forEach(key -> {
                var record = values.get(profile(key));
                if (record != null) {
                    result.put(key, new StoredProfileDocument(record.value(), record.revision()));
                }
            });
            return Map.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<Boolean> commitProfileDocuments(final Collection<ProfileDocumentChange> changes) {
        return commit(profileChanges(changes));
    }

    @Override
    public CompletableFuture<Long> profileDatabaseTime() {
        return readiness().thenCompose(ignored -> commands.time().toCompletableFuture())
            .thenApply(time -> Long.parseLong(time.get(0)) * 1000 + Long.parseLong(time.get(1)) / 1000);
    }

    @Override
    public CompletableFuture<Optional<StoredGlobalData>> loadGlobalData(final String owner, final String key) {
        observedGlobals.add(new GlobalDataReference(owner, key));
        String field = global(owner, key);
        return read(List.of(field)).thenApply(values -> Optional.ofNullable(values.get(field))
            .map(record -> new StoredGlobalData(record.value(), record.revision())));
    }

    @Override
    public CompletableFuture<StoredGlobalData> setGlobalData(final String owner, final String key, final String value) {
        return setGlobalData(owner, key, value, 0);
    }

    @Override
    public CompletableFuture<Optional<StoredGlobalData>> compareAndSetGlobalData(
        final String owner, final String key, final long revision, final String value
    ) {
        return commit(List.of(new Change(global(owner, key), revision, value, 0, false))).thenApply(committed -> {
            if (committed) {
                notifyChange(new GlobalDataReference(owner, key));
                return Optional.of(new StoredGlobalData(value, revision + 1));
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Boolean> resetGlobalData(final String owner, final String key) {
        return loadGlobalData(owner, key).thenCompose(current -> {
            if (current.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return commit(List.of(new Change(global(owner, key), current.orElseThrow().revision(), null, 0, true)))
                .thenApply(committed -> {
                    if (committed) {
                        notifyChange(new GlobalDataReference(owner, key));
                    }
                    return committed;
                });
        });
    }

    @Override
    public AutoCloseable subscribeGlobalDataChanges(final Consumer<GlobalDataReference> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public CompletableFuture<PlayerIdentity> recordPlayerIdentity(final UUID playerId, final String name) {
        return profileDatabaseTime().thenCompose(now -> findPlayerIdentity(playerId).thenCompose(previous -> {
            var identity = new PlayerIdentity(playerId, name, Instant.ofEpochMilli(now));
            List<Change> changes = new ArrayList<>();
            previous.filter(value -> !value.name().equalsIgnoreCase(name)).ifPresent(value ->
                changes.add(new Change("n|" + value.name().toLowerCase(Locale.ROOT), -1, null, 0, true)));
            changes.add(new Change("i|" + playerId, -1, json(Map.of("id", playerId.toString(), "name", name,
                "updatedAt", Long.toString(now))), 0, false));
            changes.add(new Change("n|" + name.toLowerCase(Locale.ROOT), -1, playerId.toString(), 0, false));
            return commit(changes).thenApply(ignored -> identity);
        }));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final UUID playerId) {
        String field = "i|" + playerId;
        return read(List.of(field)).thenApply(values -> Optional.ofNullable(values.get(field)).map(record -> {
            Map<String, String> value = parse(record.value(), STRING_MAP);
            Instant updated;
            try {
                updated = Instant.ofEpochMilli(Long.parseLong(value.get("updatedAt")));
            } catch (NumberFormatException failure) {
                // Legacy identity timestamps are informational; normalize the JDBC representation during migration.
                updated = Instant.EPOCH;
            }
            return new PlayerIdentity(UUID.fromString(value.get("id")), value.get("name"), updated);
        }));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(final String name) {
        String field = "n|" + name.toLowerCase(Locale.ROOT);
        return read(List.of(field)).thenCompose(values -> {
            var record = values.get(field);
            return record == null ? CompletableFuture.completedFuture(Optional.empty())
                : findPlayerIdentity(UUID.fromString(record.value()));
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUniqueId(final String owner, final String name) {
        return findPlayerIdentity(name).thenApply(identity -> identity.map(PlayerIdentity::uniqueId));
    }

    @Override
    public CompletableFuture<Integer> reset(
        final String owner, final UUID playerId, final Collection<String> containers
    ) {
        List<Change> changes = containers.stream().map(key ->
            new Change(account(owner, playerId, key), -1, null, 0, true)).toList();
        return commit(changes).thenApply(committed -> {
            if (!committed) {
                throw new IllegalStateException("Account reset conflicted");
            }
            forgetAccounts(changes);
            return changes.isEmpty() ? 0 : 1;
        });
    }

    @Override
    public CompletableFuture<Integer> resetAll(final String owner, final Collection<String> containers) {
        // An administrative scan is bounded per transaction and never runs on the Minecraft thread.
        return readiness().thenCompose(ignored -> commands.hkeys(state()).toCompletableFuture()).thenCompose(keys -> {
            List<String> selected = keys.stream().filter(key -> key.startsWith("a|" + owner + '|'))
                .filter(key -> containers.contains(key.substring(key.lastIndexOf('|') + 1))).toList();
            CompletableFuture<Void> reset = CompletableFuture.completedFuture(null);
            for (int offset = 0; offset < selected.size(); offset += 512) {
                List<Change> batch = selected.subList(offset, Math.min(offset + 512, selected.size())).stream()
                    .map(key -> new Change(key, -1, null, 0, true)).toList();
                reset = reset.thenCompose(ignored -> commit(batch).thenAccept(committed -> {
                    if (!committed) {
                        throw new IllegalStateException("Account reset batch conflicted");
                    }
                    forgetAccounts(batch);
                }));
            }
            int players = (int) selected.stream().map(key -> key.split("\\|")[2]).distinct().count();
            return reset.thenApply(ignored -> players);
        });
    }

    @Override
    public synchronized CompletableFuture<Void> checkpoint() {
        if (!checkpointRunning.compareAndSet(false, true)) {
            return checkpointFuture;
        }
        checkpointFuture = readiness().thenRunAsync(this::writeCheckpoint, worker).whenComplete((ignored, failure) ->
            checkpointRunning.set(false));
        return checkpointFuture;
    }

    @Override
    public void requestCheckpoint() {
        if (!closed.get() && checkpointRequested.compareAndSet(false, true)) {
            try {
                worker.schedule(() -> {
                    checkpointRequested.set(false);
                    scheduledCheckpoint().exceptionally(failure -> {
                        logger.log(Level.SEVERE, "Requested Redis checkpoint failed", failure);
                        return null;
                    });
                }, 10, TimeUnit.SECONDS);
            } catch (RejectedExecutionException failure) {
                checkpointRequested.set(false);
                if (!closed.get()) {
                    logger.log(Level.SEVERE, "Unable to schedule Redis checkpoint", failure);
                }
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        worker.shutdownNow();
        if (connection != null) {
            connection.close();
        }
        client.shutdown();
        durable.close();
        listeners.clear();
        accountRevisions.clear();
    }

    private CompletableFuture<Map<String, String>> loadAccounts(
        final String owner, final UUID playerId, final Collection<DataContainerKey<?>> keys, final boolean writable
    ) {
        return read(keys.stream().map(key -> account(owner, playerId, key.getName())).toList()).thenApply(values -> {
            Map<String, String> result = new LinkedHashMap<>();
            keys.forEach(key -> {
                String field = account(owner, playerId, key.getName());
                var record = values.get(field);
                if (writable) {
                    accountRevisions.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                        .put(field, record == null ? 0L : record.revision());
                }
                if (record != null) {
                    result.put(key.getName(), record.value());
                }
            });
            return Map.copyOf(result);
        });
    }

    private CompletableFuture<StoredGlobalData> setGlobalData(
        final String owner, final String key, final String value, final int attempt
    ) {
        if (attempt >= 16) {
            return CompletableFuture.failedFuture(new IllegalStateException("Global value remains contested after 16 attempts"));
        }
        return loadGlobalData(owner, key).thenCompose(current -> compareAndSetGlobalData(owner, key,
            current.map(StoredGlobalData::revision).orElse(0L), value)).thenCompose(stored -> stored
            .map(CompletableFuture::completedFuture).orElseGet(() -> setGlobalData(owner, key, value, attempt + 1)));
    }

    private void initialize() {
        connection = client.connect();
        commands = connection.async();
        commands.clientSetname(settings.clientName()).toCompletableFuture().join();
        String server = commands.info("server").toCompletableFuture().join();
        String version = server.lines().filter(line -> line.startsWith("redis_version:"))
            .map(line -> line.substring("redis_version:".length())).findFirst().orElse("0");
        if (Integer.parseInt(version.split("\\.", 2)[0]) < 7) {
            throw new IllegalStateException("Redis 7 or newer is required for atomic ACL preflight");
        }
        // One initializer owns the cold recovery. Every later command validates this new epoch.
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (true) {
            String existing = commands.get(meta()).toCompletableFuture().join();
            if (existing != null) {
                epoch = existing;
                var checkpoint = checkpoints.head(settings.prefix()).join();
                if (comparePositions(tail(), checkpoint.position()) < 0) {
                    throw new IllegalStateException("Redis recovered behind SQL; coordinated cold recovery is required");
                }
                requireCheckpointAnchor(checkpoint.position());
                break;
            }
            if ("OK".equals(commands.set(lock("bootstrap"), token, SetArgs.Builder.nx().px(60000))
                .toCompletableFuture().join())) {
                try {
                    var checkpoint = checkpoints.load(settings.prefix()).join();
                    if (!checkpoints.activateEpoch(settings.prefix(), checkpoint.generation(), token).join()) {
                        throw new IllegalStateException("SQL checkpoint advanced during recovery; retry startup");
                    }
                    String staging = state() + ':' + token;
                    commands.hset(staging, "__schema", "1").toCompletableFuture().join();
                    commands.hset(staging, "__position", checkpoint.position()).toCompletableFuture().join();
                    commands.pexpire(staging, 120000).toCompletableFuture().join();
                    List<Map.Entry<String, String>> values = new ArrayList<>(checkpoint.values().entrySet());
                    for (int offset = 0; offset < values.size(); offset += 512) {
                        Map<String, String> batch = new LinkedHashMap<>();
                        values.subList(offset, Math.min(offset + 512, values.size()))
                            .forEach(entry -> batch.put(entry.getKey(), entry.getValue()));
                        commands.hset(staging, batch).toCompletableFuture().join();
                    }
                    Long installed = commands.<Long>eval(BOOTSTRAP, ScriptOutputType.INTEGER,
                        new String[]{lock("bootstrap"), meta(), staging, state(), journal(), receipts(), receiptAges()},
                        token, token, checkpoint.position()).toCompletableFuture().join();
                    if (installed != 1) {
                        throw new IllegalStateException("Cold recovery lease expired; refusing partial initialization");
                    }
                    commands.persist(state()).toCompletableFuture().join();
                    epoch = token;
                    break;
                } finally {
                    commands.eval(UNLOCK, ScriptOutputType.INTEGER, new String[]{lock("bootstrap")}, token)
                        .toCompletableFuture().join();
                }
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out awaiting authoritative Redis recovery");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Redis initialization interrupted", failure);
            }
        }
        // Reject a missing authoritative hash rather than resurrecting individual stale SQL values.
        if (!commands.hexists(state(), "__schema").toCompletableFuture().join()) {
            throw new IllegalStateException("Redis live state is incomplete; coordinated recovery is required");
        }
        observedPosition = tail();
    }

    private CompletableFuture<Map<String, StoredRecord>> read(final Collection<String> keys) {
        requireBounded(keys.size());
        return readiness().thenCompose(ignored -> commands.<String>eval(READ, ScriptOutputType.VALUE,
            new String[]{state(), meta()}, epoch, json(keys)).toCompletableFuture()).thenApply(encoded -> {
                Map<String, StoredRecord> result = new LinkedHashMap<>();
                parse(encoded, STRING_MAP).forEach((key, value) -> result.put(key, parseRecord(value)));
                return Map.copyOf(result);
            });
    }

    private CompletableFuture<Boolean> commit(final List<Change> changes) {
        requireBounded(changes.size());
        if (changes.stream().map(Change::key).distinct().count() != changes.size()) {
            throw new IllegalArgumentException("Duplicate Redis transaction field");
        }
        if (changes.isEmpty()) {
            return readiness().thenApply(ignored -> true);
        }
        String payload = json(changes);
        if (payload.length() > 16_777_216) {
            throw new IllegalArgumentException("Redis transaction exceeds 16 MiB");
        }
        if (!pending.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis command queue is full"));
        }
        String operation = UUID.randomUUID().toString();
        return profileDatabaseTime().thenCompose(createdAt -> execute(operation, payload, createdAt).exceptionallyCompose(first ->
            // Reuse the operation ID after an ambiguous network failure; never execute a second grant.
            execute(operation, payload, createdAt).exceptionallyCompose(second ->
                CompletableFuture.failedFuture(new IndeterminateCommitException(second)))))
            .thenApply(outcome -> !outcome.equals("CONFLICT"))
            .whenComplete((ignored, failure) -> pending.release());
    }

    private CompletableFuture<String> execute(final String operation, final String payload, final long createdAt) {
        return commands.<String>eval(COMMIT, ScriptOutputType.VALUE,
            new String[]{state(), meta(), journal(), receipts(), receiptAges()}, epoch, operation, payload,
            Integer.toString(settings.maximumJournalEntries()), Long.toString(createdAt)).toCompletableFuture();
    }

    private void writeCheckpoint() {
        String token = UUID.randomUUID().toString();
        if (!"OK".equals(commands.set(lock("checkpoint"), token, SetArgs.Builder.nx().px(60000))
            .toCompletableFuture().join())) {
            return;
        }
        try {
            assertEpoch();
            var baseline = checkpoints.head(settings.prefix()).join();
            requireCheckpointAnchor(baseline.position());
            String cut = tail();
            if (cut.equals(baseline.position())) {
                pruneReceipts();
                return;
            }
            Map<String, String> changes = new LinkedHashMap<>();
            Set<String> removed = new HashSet<>();
            String position = baseline.position();
            while (!position.equals(cut)) {
                var entries = commands.xrange(journal(), Range.create('(' + position, cut), Limit.from(512))
                    .toCompletableFuture().join();
                if (entries.isEmpty()) {
                    throw new IllegalStateException("Redis journal is incomplete; refusing a mixed checkpoint");
                }
                for (var entry : entries) {
                    applyJournal(changes, removed, entry);
                    position = entry.getId();
                }
                if (!token.equals(commands.get(lock("checkpoint")).toCompletableFuture().join())) {
                    throw new IllegalStateException("Checkpoint coordinator lease expired");
                }
                commands.pexpire(lock("checkpoint"), 60000).toCompletableFuture().join();
            }
            assertEpoch();
            if (checkpoints.publishChanges(settings.prefix(),
                new NetworkCheckpointChanges(baseline.generation(), cut, changes, removed), epoch).join()) {
                // Keep the cut entry as a continuity anchor. Only acknowledged, checkpointed history may be trimmed.
                if (!cut.equals("0-0")) {
                    commands.xtrim(journal(), new XTrimArgs().minId(cut).exactTrimming()).toCompletableFuture().join();
                }
                pruneReceipts();
            }
        } finally {
            commands.eval(UNLOCK, ScriptOutputType.INTEGER, new String[]{lock("checkpoint")}, token)
                .toCompletableFuture().join();
        }
    }

    private void pollInvalidations() {
        if (!initialization.isDone() || initialization.isCompletedExceptionally() || closed.get() || listeners.isEmpty()) {
            return;
        }
        try {
            assertEpoch();
            var first = commands.xrange(journal(), Range.unbounded(), Limit.from(1)).toCompletableFuture().join();
            if (!first.isEmpty() && comparePositions(observedPosition, first.getFirst().getId()) < 0) {
                // Checkpoint trimming can overtake a disconnected reader. Revalidate every locally cached global value.
                observedGlobals.forEach(this::notifyChange);
                observedPosition = first.getFirst().getId();
            }
            var entries = commands.xrange(journal(), Range.create('(' + observedPosition, "+"), Limit.from(512))
                .toCompletableFuture().join();
            if (!entries.isEmpty()) {
                for (var entry : entries) {
                    Map<String, Object> changes = parse(entry.getBody().get("changes"), CHANGE_MAP);
                    changes.keySet().stream().filter(key -> key.startsWith("g|")).forEach(key -> {
                        String[] parts = key.split("\\|", 3);
                        notifyChange(new GlobalDataReference(parts[1], parts[2]));
                    });
                    observedPosition = entry.getId();
                }
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Redis invalidation polling failed; authoritative operations remain guarded", failure);
        }
    }

    private CompletableFuture<Void> scheduledCheckpoint() {
        // Coalesce across the whole network, not once per backend with the same full SQL snapshot.
        return readiness().thenCompose(ignored -> commands.set(lock("checkpoint-request"), epoch,
            SetArgs.Builder.nx().px(10000)).toCompletableFuture()).thenCompose(acquired ->
                "OK".equals(acquired) ? checkpoint() : CompletableFuture.completedFuture(null));
    }

    private void pruneReceipts() {
        // Scripts reject operations older than one minute. Retain receipts for two minutes before removing them.
        String script = "local t = redis.call('TIME'); local cutoff = tonumber(t[1]) * 1000 - 120000; "
            + "local ids = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', cutoff, 'LIMIT', 0, 512); "
            + "for _, id in ipairs(ids) do redis.call('HDEL', KEYS[1], id); redis.call('ZREM', KEYS[2], id) end "
            + "return #ids";
        while (commands.<Long>eval(script, ScriptOutputType.INTEGER, new String[]{receipts(), receiptAges()})
            .toCompletableFuture().join() == 512) {
            // Bound every Redis script; a large batch must not monopolize the authority thread.
        }
    }

    private void notifyChange(final GlobalDataReference reference) {
        listeners.forEach(listener -> listener.accept(reference));
    }

    private static int comparePositions(final String left, final String right) {
        String[] a = left.split("-", 2);
        String[] b = right.split("-", 2);
        int milliseconds = Long.compare(Long.parseLong(a[0]), Long.parseLong(b[0]));
        return milliseconds == 0 ? Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])) : milliseconds;
    }

    private void assertEpoch() {
        if (fencingFailure != null) {
            throw new IllegalStateException("Redis instance has lost network ownership", fencingFailure);
        }
        if (!epoch.equals(commands.get(meta()).toCompletableFuture().join())) {
            throw new IllegalStateException("Redis live epoch changed; this instance must restart");
        }
    }

    private String tail() {
        var entries = commands.xrevrange(journal(), Range.unbounded(), Limit.from(1)).toCompletableFuture().join();
        return entries.isEmpty() ? "0-0" : entries.getFirst().getId();
    }

    private void requireCheckpointAnchor(final String position) {
        if (!position.equals("0-0") && commands.xrange(journal(), Range.create(position, position), Limit.from(1))
            .toCompletableFuture().join().isEmpty()) {
            throw new IllegalStateException("Redis checkpoint anchor is missing; coordinated recovery is required");
        }
    }

    private void applyJournal(
        final Map<String, String> values, final Set<String> removed, final StreamMessage<String, String> entry
    ) {
        Map<String, Object> changes = parse(entry.getBody().get("changes"), CHANGE_MAP);
        changes.forEach((key, value) -> {
            if (Boolean.FALSE.equals(value)) {
                values.remove(key);
                removed.add(key);
            } else {
                values.put(key, (String) value);
                removed.remove(key);
            }
        });
    }

    private List<Change> profileChanges(final Collection<ProfileDocumentChange> changes) {
        return new ArrayList<>(changes.stream().map(change -> new Change(profile(change.key()),
            change.expectedRevision(), change.value(), change.notAfterEpochMillis(), false)).toList());
    }

    private StoredRecord parseRecord(final String value) {
        try {
            return mapper.readValue(value, StoredRecord.class);
        } catch (IOException failure) {
            throw new IllegalStateException("Invalid Redis record", failure);
        }
    }

    private void forgetAccounts(final Collection<Change> changes) {
        changes.forEach(change -> {
            UUID playerId = UUID.fromString(change.key().split("\\|", 4)[2]);
            Map<String, Long> revisions = accountRevisions.get(playerId);
            if (revisions != null) {
                revisions.remove(change.key());
            }
        });
    }

    private <T> T parse(final String value, final TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (IOException failure) {
            throw new IllegalStateException("Invalid Redis value", failure);
        }
    }

    private String json(final Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to serialize Redis value", failure);
        }
    }

    private static String profile(final ProfileDocumentKey key) {
        return "p|" + key.address().storageKey() + '|' + key.owner() + '|' + key.name();
    }

    private static String account(final String owner, final UUID playerId, final String key) {
        return "a|" + owner + '|' + playerId + '|' + key;
    }

    private static String global(final String owner, final String key) {
        return "g|" + owner + '|' + key;
    }

    private String state() {
        return settings.prefix() + "{network}:state";
    }

    private String meta() {
        return settings.prefix() + "{network}:epoch";
    }

    private String journal() {
        return settings.prefix() + "{network}:journal";
    }

    private String receipts() {
        return settings.prefix() + "{network}:receipts";
    }

    private String lock(final String kind) {
        return settings.prefix() + "{network}:" + kind + "-lock";
    }

    private String receiptAges() {
        return settings.prefix() + "{network}:receipt-ages";
    }

    private static void requireBounded(final int size) {
        if (size > 1024) {
            throw new IllegalArgumentException("Redis transaction exceeds 1024 fields");
        }
    }

    private static String resource(final String name) {
        try (var stream = RedisPlayerDataStore.class.getResourceAsStream("/redis/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load Redis script", failure);
        }
    }

    private record StoredRecord(String value, long revision) {
    }

    private record Change(String key, long expected, String value, long deadline, boolean remove) {
    }
}
