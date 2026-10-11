package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.Getter;
import lombok.Value;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds the shared identity and registered data containers of an online Vex player.
 *
 * <p>Container access is synchronized per container. Callers should use {@link #read} for stable
 * reads and {@link #update(DataContainerKey, Consumer)} for mutations that must be persisted.
 */
public final class VexPlayer {

    private static final PlayerContainer[] EMPTY_FEATURE_CONTAINERS = new PlayerContainer[0];
    private static final PlayerContainerLookup EMPTY_CONTAINER_LOOKUP = ignored -> -1;

    @Getter
    private final UUID uniqueId;
    @Getter
    private volatile String name;
    private final Map<DataContainerKey<?>, ContainerState<?>> containers = new ConcurrentHashMap<>();
    private final Map<ProfileDomain, PlayerProfileContext> profileContexts = new ConcurrentHashMap<>();
    private Predicate<PlayerProfileContext> profileAccess = ignored -> true;
    private final PlayerContainerLookup containerLookup;
    private volatile PlayerContainer[] featureContainers = EMPTY_FEATURE_CONTAINERS;
    private volatile Object platformPlayer;
    private List<Runnable> committedEffects;
    private List<Runnable> durableEffects;
    private List<Runnable> rollbackEffects;
    private Map<DataContainerKey<?>, ContainerSnapshot<Object>> transactionSnapshots;
    private Function<Object, Object> transactionCopier;
    private ProfileDomain mutationDomain;

    /** Creates an initially empty player instance for the data coordinator. */
    @ApiStatus.Internal
    public VexPlayer(final UUID uniqueId, final String name) {
        this(uniqueId, name, EMPTY_CONTAINER_LOOKUP);
    }

    /** Creates a player backed by the supplied feature-container registry. */
    @ApiStatus.Internal
    public VexPlayer(final UUID uniqueId, final String name, final PlayerContainerLookup containerLookup) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
        this.containerLookup = Objects.requireNonNull(containerLookup, "containerLookup");
    }

    /** Limits one guarded mutation to opted-in containers in its domain; unrelated account writes remain independent. */
    public synchronized <T> T inProfileMutation(final ProfileDomain domain, final Supplier<T> operation) {
        if (mutationDomain != null) {
            throw new IllegalStateException("Nested profile mutation");
        }
        mutationDomain = Objects.requireNonNull(domain, "domain");
        try {
            return Objects.requireNonNull(operation, "operation").get();
        } finally {
            mutationDomain = null;
        }
    }

    /** Copies containers on their first write and restores those values when the economic operation fails. */
    public boolean atomic(final Function<Object, Object> copier, final BooleanSupplier operation) {
        boolean success = false;
        List<Runnable> effects;

        synchronized (this) {
            if (committedEffects != null) {
                throw new IllegalStateException("Nested player transaction");
            }

            Map<DataContainerKey<?>, ContainerSnapshot<Object>> before = new LinkedHashMap<>();

            transactionCopier = Objects.requireNonNull(copier, "copier");
            transactionSnapshots = before;
            committedEffects = new ArrayList<>();
            rollbackEffects = new ArrayList<>();

            try {
                success = operation.getAsBoolean();
            } finally {
                effects = committedEffects;
                committedEffects = null;
                List<Runnable> compensations = rollbackEffects;
                rollbackEffects = null;
                transactionSnapshots = null;
                transactionCopier = null;

                if (!success) {
                    for (Runnable compensation : compensations.reversed()) {
                        try {
                            compensation.run();
                        } catch (RuntimeException failure) {
                            System.getLogger(VexPlayer.class.getName()).log(System.Logger.Level.ERROR,
                                "Player transaction compensation failed for " + uniqueId, failure);
                        }
                    }
                    before.forEach((key, snapshot) -> {
                        if (stateUnchecked(key).snapshot(ignored -> null).getRevision() != snapshot.getRevision()) {
                            restoreValue(key, snapshot.getValue());
                        }
                    });
                }
            }
        }

        if (success) {
            effects.forEach(effect -> {
                try {
                    afterCommit(effect);
                } catch (RuntimeException failure) {
                    System.getLogger(VexPlayer.class.getName())
                        .log(
                            System.Logger.Level.ERROR,
                            "Post-commit player notification failed for " + uniqueId,
                            failure
                        );
                }
            });
        }

        return success;
    }

    /** Defers observer notifications until an enclosing player transaction has committed. */
    public void afterCommit(final Runnable effect) {
        Objects.requireNonNull(effect, "effect");

        synchronized (this) {
            if (committedEffects != null) {
                committedEffects.add(effect);

                return;
            }
            if (durableEffects != null) {
                durableEffects.add(effect);
                return;
            }
        }

        effect.run();
    }

    /** Defers post-commit effects across an asynchronous durability boundary on the platform thread. */
    @ApiStatus.Internal
    public synchronized void beginDurableEffects() {
        if (durableEffects != null || committedEffects != null) {
            throw new IllegalStateException("A durable player operation is already active");
        }
        durableEffects = new ArrayList<>();
    }

    /** Releases only committed effects; failed writes never publish rewards or observer notifications. */
    @ApiStatus.Internal
    public void finishDurableEffects(final boolean committed) {
        List<Runnable> effects;
        synchronized (this) {
            effects = durableEffects;
            durableEffects = null;
        }
        if (committed && effects != null) {
            effects.forEach(this::afterCommit);
        }
    }

    /** Registers an external compensation for the active transaction; standalone successful actions need no undo. */
    public synchronized void afterRollback(final Runnable effect) {
        Objects.requireNonNull(effect, "effect");
        if (rollbackEffects != null) {
            rollbackEffects.add(effect);
        }
    }

    /** Returns the required feature container bound to this player. */
    public <T extends PlayerContainer> T getContainer(final Class<T> type) {
        Class<T> checkedType = Objects.requireNonNull(type, "type");
        int slot = containerLookup.findSlot(checkedType);
        PlayerContainer[] current = featureContainers;

        if (slot < 0 || slot >= current.length || current[slot] == null) {
            throw new IllegalStateException("Player container is not available: " + checkedType.getName());
        }

        return checkedType.cast(current[slot]);
    }

    /** Finds an optional feature container bound to this player. */
    public <T extends PlayerContainer> Optional<T> findContainer(final Class<T> type) {
        Class<T> checkedType = Objects.requireNonNull(type, "type");
        int slot = containerLookup.findSlot(checkedType);
        PlayerContainer[] current = featureContainers;

        return slot < 0 || slot >= current.length || current[slot] == null ? Optional.empty()
            : Optional.of(checkedType.cast(current[slot]));
    }

    /** Returns the native platform player for this session. */
    public <T> T requirePlatformPlayer(final Class<T> type) {
        Class<T> checkedType = Objects.requireNonNull(type, "type");
        Object current = platformPlayer;

        if (!checkedType.isInstance(current)) {
            throw new IllegalStateException("Platform player is not available as " + checkedType.getName());
        }

        return checkedType.cast(current);
    }

    /** Finds the native platform player for this session when it has the requested type. */
    public <T> Optional<T> findPlatformPlayer(final Class<T> type) {
        Class<T> checkedType = Objects.requireNonNull(type, "type");
        Object current = platformPlayer;

        return checkedType.isInstance(current) ? Optional.of(checkedType.cast(current)) : Optional.empty();
    }

    /** Installs a registered feature container in its dense runtime slot. */
    @ApiStatus.Internal
    public synchronized <T extends PlayerContainer> void installContainer(
        final int slot,
        final Class<T> type,
        final T container
    ) {
        if (slot < 0) {
            throw new IllegalArgumentException("Container slot must not be negative");
        }

        Class<T> checkedType = Objects.requireNonNull(type, "type");
        T checkedContainer = checkedType.cast(Objects.requireNonNull(container, "container"));
        PlayerContainer[] current = featureContainers;

        if (slot >= current.length) {
            current = Arrays.copyOf(current, slot + 1);
        } else {
            current = current.clone();
        }

        if (current[slot] != null) {
            throw new IllegalStateException("Player container slot is already installed: " + slot);
        }

        current[slot] = checkedContainer;
        featureContainers = current;
    }

    /** Removes and closes a registered feature container. */
    @ApiStatus.Internal
    public synchronized void removeContainer(final int slot) {
        PlayerContainer[] current = featureContainers;

        if (slot < 0 || slot >= current.length || current[slot] == null) {
            return;
        }

        PlayerContainer removed = current[slot];

        current = current.clone();
        current[slot] = null;
        featureContainers = current;
        removed.close();
    }

    /** Closes every feature container attached to this session. */
    @ApiStatus.Internal
    public synchronized void closeContainers() {
        PlayerContainer[] current = featureContainers;

        featureContainers = EMPTY_FEATURE_CONTAINERS;
        RuntimeException failure = null;

        for (int index = current.length - 1; index >= 0; index--) {
            PlayerContainer container = current[index];

            if (container == null) {
                continue;
            }

            try {
                container.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    /** Binds the native platform player to this loaded session. */
    @ApiStatus.Internal
    public void bindPlatformPlayer(final Object platformPlayer) {
        Object checkedPlayer = Objects.requireNonNull(platformPlayer, "platformPlayer");
        Object current = this.platformPlayer;

        if (current != null && current != checkedPlayer) {
            throw new IllegalStateException("A different platform player is already bound");
        }

        this.platformPlayer = checkedPlayer;
    }

    /** Removes the native platform-player reference from this session. */
    @ApiStatus.Internal
    public void unbindPlatformPlayer() {
        platformPlayer = null;
    }

    /** Finds a ready profile context without changing the account's stable identity. */
    public Optional<PlayerProfileContext> findProfileContext(final ProfileDomain domain) {
        return Optional.ofNullable(profileContexts.get(Objects.requireNonNull(domain, "domain")));
    }

    /** Checks the complete captured identity, including reconnect and membership generation. */
    public boolean isCurrentContext(final PlayerProfileContext context) {
        return Objects.requireNonNull(context, "context").equals(profileContexts.get(context.domain()));
    }

    /** Captures every installed domain session for an asynchronous operation. */
    public Map<ProfileDomain, PlayerProfileContext> profileContexts() {
        return Map.copyOf(profileContexts);
    }

    /** Binds a cached ownership check without adding database requests to container access. */
    @ApiStatus.Internal
    public void setProfileAccessGuard(final Predicate<PlayerProfileContext> access) {
        profileAccess = Objects.requireNonNull(access, "access");
    }

    /** Resolves a key's explicit persistence address; missing profile contexts fail closed. */
    public PlayerDataAddress addressOf(final DataContainerKey<?> key) {
        return Objects.requireNonNull(key, "key").getScope().domain()
            .map(domain -> findProfileContext(domain).orElseThrow(() -> new IllegalStateException(
                "Profile context is not ready: " + domain.value())).address())
            .orElseGet(() -> PlayerDataAddress.account(uniqueId));
    }

    /** Installs a complete detached profile before notifying feature facades. */
    @ApiStatus.Internal
    public synchronized void installProfile(
        final PlayerProfileContext context,
        final Map<DataContainerKey<?>, Object> values
    ) {
        Objects.requireNonNull(context, "context");
        if (!uniqueId.equals(context.playerId()) || transactionSnapshots != null) {
            throw new IllegalStateException("Cannot install a profile inside another player's transaction");
        }
        Map<DataContainerKey<?>, ContainerState<?>> replacement = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!key.getScope().domain().filter(context.domain()::equals).isPresent()) {
                throw new IllegalArgumentException("Profile value belongs to another scope: " + key.getName());
            }
            replacement.put(key, profileState(key, value));
        });
        for (DataContainerKey<?> key : containers.keySet()) {
            if (key.getScope().domain().filter(context.domain()::equals).isPresent() && !replacement.containsKey(key)) {
                throw new IllegalArgumentException("Incomplete profile snapshot: " + key.getName());
            }
        }
        PlayerProfileContext previous = profileContexts.put(context.domain(), context);
        containers.putAll(replacement);
        for (PlayerContainer container : featureContainers) {
            if (container != null) {
                container.onProfileChanged(previous, context);
            }
        }
    }

    /** Restores detached values of a retired platform session without invoking platform-dependent facades. */
    @ApiStatus.Internal
    public synchronized void rollbackRetiredProfile(
        final PlayerProfileContext context, final Map<DataContainerKey<?>, Object> values
    ) {
        if (platformPlayer != null || !isCurrentContext(context)) {
            throw new IllegalStateException("Only an unbound captured session can be rolled back without notifications");
        }
        values.forEach((key, value) -> containers.put(key, profileState(key, value)));
    }

    /** Acknowledges only the same scope and revision captured by a completed storage write. */
    @ApiStatus.Internal
    public synchronized void markClean(
        final DataContainerKey<?> key,
        final PlayerProfileContext context,
        final long revision
    ) {
        if (context == null && key.getScope().domain().isEmpty() || context != null && isCurrentContext(context)) {
            markClean(key, revision);
        }
    }

    /** Returns a registered container without marking it as changed */
    public <T> T require(final DataContainerKey<T> key) {
        return state(key).read();
    }

    /** Reads a value from a container while its state remains stable */
    public synchronized <T, R> R read(final DataContainerKey<T> key, final Function<T, R> reader) {
        return state(key).read(Objects.requireNonNull(reader, "reader"));
    }

    /** Updates a container atomically and marks it for persistence */
    public synchronized <T> void update(final DataContainerKey<T> key, final Consumer<T> update) {
        Objects.requireNonNull(update, "update");
        snapshotBeforeWrite(key);
        state(key).update(value -> {
            update.accept(value);

            return null;
        });
    }

    /** Updates a container atomically and returns a value from the same operation */
    public synchronized <T, R> R update(final DataContainerKey<T> key, final Function<T, R> update) {
        Objects.requireNonNull(update, "update");
        snapshotBeforeWrite(key);
        return state(key).update(update);
    }

    /** Replaces an immutable container value and marks its new revision for persistence. */
    public synchronized <T> void replace(final DataContainerKey<T> key, final T value) {
        snapshotBeforeWrite(key);
        state(key).replace(key.getType().cast(Objects.requireNonNull(value, "value")));
    }

    /** Mutates a container, marking it dirty only when the callback reports a change. */
    public synchronized <T> boolean updateIfChanged(final DataContainerKey<T> key, final Predicate<T> update) {
        Objects.requireNonNull(update, "update");
        snapshotBeforeWrite(key);
        return state(key).updateIfChanged(update);
    }

    /** Checks whether the requested container is available on this player */
    public boolean has(final DataContainerKey<?> key) {
        return containers.containsKey(Objects.requireNonNull(key, "key"));
    }

    /** Installs a clean container value when the key is not present yet. */
    @ApiStatus.Internal
    public <T> void install(final DataContainerKey<T> key, final T value) {
        install(key, value, false);
    }

    /** Installs a container value with an explicit initial dirty state. */
    @ApiStatus.Internal
    public <T> void install(final DataContainerKey<T> key, final T value, final boolean dirty) {
        Objects.requireNonNull(key, "key");
        T checkedValue = key.getType().cast(Objects.requireNonNull(value, "value"));

        containers.putIfAbsent(key, new ContainerState<>(checkedValue, dirty));
    }

    /** Updates the last known player name. */
    @ApiStatus.Internal
    public void setName(final String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Returns the keys whose values have changed since their last successful save. */
    @ApiStatus.Internal
    public Set<DataContainerKey<?>> getDirtyKeys() {
        return containers.entrySet()
            .stream()
            .filter(entry -> entry.getValue().isDirty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns a registered container value without its generic type information. */
    @ApiStatus.Internal
    public Object getValue(final DataContainerKey<?> key) {
        return stateUnchecked(key).read();
    }

    /** Creates a serialized snapshot paired with the container revision it represents. */
    @ApiStatus.Internal
    public synchronized <R> ContainerSnapshot<R> snapshot(
        final DataContainerKey<?> key,
        final Function<Object, R> snapshotter
    ) {
        return stateUnchecked(key).snapshot(Objects.requireNonNull(snapshotter, "snapshotter"));
    }

    /** Captures installed state for operator recovery after lease expiry, without permitting any mutation. */
    @ApiStatus.Internal
    public synchronized <R> ContainerSnapshot<R> recoverySnapshot(
        final DataContainerKey<?> key, final Function<Object, R> snapshotter
    ) {
        ContainerState<?> state = containers.get(Objects.requireNonNull(key, "key"));
        if (state == null) {
            throw new IllegalStateException("Recovery container is not installed");
        }
        return state.snapshot(Objects.requireNonNull(snapshotter, "snapshotter"));
    }

    /** Marks a container clean if it has not changed since the supplied revision was saved. */
    @ApiStatus.Internal
    public void markClean(final DataContainerKey<?> key, final long revision) {
        stateUnchecked(key).markClean(revision);
    }

    /** Replaces one persistent value with a fresh default and informs feature containers. */
    @ApiStatus.Internal
    public synchronized void reset(final DataContainerKey<?> key) {
        DataContainerKey<?> checkedKey = Objects.requireNonNull(key, "key");

        snapshotBeforeWrite(checkedKey);
        resetUnchecked(checkedKey);

        for (PlayerContainer container : featureContainers) {
            if (container != null) {
                container.onDataReset(checkedKey);
            }
        }
    }

    private <T> void restoreValue(final DataContainerKey<T> key, final Object value) {
        state(key).reset(key.getType().cast(value));
    }

    private static <T> ContainerState<T> profileState(final DataContainerKey<T> key, final Object value) {
        return new ContainerState<>(key.getType().cast(Objects.requireNonNull(value, "value")), false);
    }

    private void snapshotBeforeWrite(final DataContainerKey<?> key) {
        if (mutationDomain != null && !key.getScope().domain().filter(mutationDomain::equals).isPresent()) {
            throw new IllegalStateException("Profile transactions may only mutate their participating domain");
        }
        if (transactionSnapshots != null && !transactionSnapshots.containsKey(key)) {
            transactionSnapshots.put(key, stateUnchecked(key).snapshot(transactionCopier));
        }
    }

    private <T> ContainerState<T> state(final DataContainerKey<T> key) {
        return castState(key, stateUnchecked(key));
    }

    private ContainerState<?> stateUnchecked(final DataContainerKey<?> key) {
        ProfileDomain domain = key.getScope().domain().orElse(null);
        if (domain != null) {
            PlayerProfileContext context = profileContexts.get(domain);
            if (context == null || !profileAccess.test(context)) {
                throw new IllegalStateException("Profile context is not writable: " + domain.value());
            }
        }
        ContainerState<?> state = containers.get(Objects.requireNonNull(key, "key"));

        if (state == null) {
            throw new IllegalStateException("Player container is not registered: " + key.getName());
        }

        return state;
    }

    @SuppressWarnings("unchecked")
    private <T> void resetUnchecked(final DataContainerKey<T> key) {
        state(key).reset(key.createDefaultValue());
    }

    @SuppressWarnings("unchecked")
    private <T> ContainerState<T> castState(final DataContainerKey<T> key, final ContainerState<?> state) {
        key.getType().cast(state.read());

        return (ContainerState<T>) state;
    }

    private static final class ContainerState<T> {

        private T value;
        private boolean dirty;
        private long revision;

        private ContainerState(final T value, final boolean dirty) {
            this.value = value;
            this.dirty = dirty;
        }

        private synchronized T read() {
            return value;
        }

        private synchronized <R> R read(final Function<T, R> reader) {
            return reader.apply(value);
        }

        private synchronized <R> R update(final Function<T, R> update) {
            try {
                return update.apply(value);
            } finally {
                // A callback may mutate before throwing; never mark that revision clean accidentally.
                dirty = true;
                revision++;
            }
        }

        private synchronized boolean isDirty() {
            return dirty;
        }

        private synchronized boolean updateIfChanged(final Predicate<T> update) {
            boolean changed = true;

            try {
                changed = update.test(value);

                return changed;
            } finally {
                if (changed) {
                    dirty = true;
                    revision++;
                }
            }
        }

        private synchronized <R> ContainerSnapshot<R> snapshot(final Function<Object, R> snapshotter) {
            return new ContainerSnapshot<>(snapshotter.apply(value), revision);
        }

        private synchronized void markClean(final long savedRevision) {
            if (revision == savedRevision) {
                dirty = false;
            }
        }

        private synchronized void reset(final T resetValue) {
            value = Objects.requireNonNull(resetValue, "resetValue");
            dirty = true;
            revision++;
        }

        private synchronized void replace(final T replacement) {
            value = replacement;
            dirty = true;
            revision++;
        }
    }

    /** Pairs a copied container value with the revision observed while copying it. */
    @Value
    public static class ContainerSnapshot<T> {

        T value;
        long revision;
    }
}
