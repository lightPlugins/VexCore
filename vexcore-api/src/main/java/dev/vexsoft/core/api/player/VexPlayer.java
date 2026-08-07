package dev.vexsoft.core.api.player;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds the shared identity and registered data containers of an online Vex player.
 *
 * <p>Container access is synchronized per container. Callers should use {@link #read} for stable
 * reads and {@link #update(DataContainerKey, Consumer)} for mutations that must be persisted.</p>
 */
public final class VexPlayer {

  private static final PlayerContainer[] EMPTY_FEATURE_CONTAINERS = new PlayerContainer[0];
  private static final PlayerContainerLookup EMPTY_CONTAINER_LOOKUP = ignored -> -1;

  @Getter
  private final UUID uniqueId;
  @Getter
  private volatile String name;
  private final Map<DataContainerKey<?>, ContainerState<?>> containers = new ConcurrentHashMap<>();
  private final PlayerContainerLookup containerLookup;
  private volatile PlayerContainer[] featureContainers = EMPTY_FEATURE_CONTAINERS;
  private volatile Object platformPlayer;

  /** Creates an initially empty player instance for the data coordinator. */
  @ApiStatus.Internal
  public VexPlayer(final UUID uniqueId, final String name) {
    this(uniqueId, name, EMPTY_CONTAINER_LOOKUP);
  }

  /** Creates a player backed by the supplied feature-container registry. */
  @ApiStatus.Internal
  public VexPlayer(
      final UUID uniqueId,
      final String name,
      final PlayerContainerLookup containerLookup
  ) {
    this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    this.name = Objects.requireNonNull(name, "name");
    this.containerLookup = Objects.requireNonNull(containerLookup, "containerLookup");
  }

  /** Returns the required feature container bound to this player. */
  public <T extends PlayerContainer> T getContainer(final Class<T> type) {
    Class<T> checkedType = Objects.requireNonNull(type, "type");
    int slot = containerLookup.findSlot(checkedType);
    PlayerContainer[] current = featureContainers;
    if (slot < 0 || slot >= current.length || current[slot] == null) {
      throw new IllegalStateException(
          "Player container is not available: " + checkedType.getName()
      );
    }
    return checkedType.cast(current[slot]);
  }

  /** Finds an optional feature container bound to this player. */
  public <T extends PlayerContainer> Optional<T> findContainer(final Class<T> type) {
    Class<T> checkedType = Objects.requireNonNull(type, "type");
    int slot = containerLookup.findSlot(checkedType);
    PlayerContainer[] current = featureContainers;
    return slot < 0 || slot >= current.length || current[slot] == null
        ? Optional.empty()
        : Optional.of(checkedType.cast(current[slot]));
  }

  /** Returns the native platform player for this session. */
  public <T> T requirePlatformPlayer(final Class<T> type) {
    Class<T> checkedType = Objects.requireNonNull(type, "type");
    Object current = platformPlayer;
    if (!checkedType.isInstance(current)) {
      throw new IllegalStateException(
          "Platform player is not available as " + checkedType.getName()
      );
    }
    return checkedType.cast(current);
  }

  /** Finds the native platform player for this session when it has the requested type. */
  public <T> Optional<T> findPlatformPlayer(final Class<T> type) {
    Class<T> checkedType = Objects.requireNonNull(type, "type");
    Object current = platformPlayer;
    return checkedType.isInstance(current)
        ? Optional.of(checkedType.cast(current))
        : Optional.empty();
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

  /** Returns a registered container without marking it as changed */
  public <T> T require(final DataContainerKey<T> key) {
    return state(key).read();
  }

  /** Reads a value from a container while its state remains stable */
  public <T, R> R read(final DataContainerKey<T> key, final Function<T, R> reader) {
    return state(key).read(Objects.requireNonNull(reader, "reader"));
  }

  /** Updates a container atomically and marks it for persistence */
  public <T> void update(final DataContainerKey<T> key, final Consumer<T> update) {
    Objects.requireNonNull(update, "update");
    state(key).update(value -> {
      update.accept(value);
      return null;
    });
  }

  /** Updates a container atomically and returns a value from the same operation */
  public <T, R> R update(final DataContainerKey<T> key, final Function<T, R> update) {
    return state(key).update(Objects.requireNonNull(update, "update"));
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
    return containers.entrySet().stream()
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
  public <R> ContainerSnapshot<R> snapshot(
      final DataContainerKey<?> key,
      final Function<Object, R> snapshotter
  ) {
    return stateUnchecked(key).snapshot(Objects.requireNonNull(snapshotter, "snapshotter"));
  }

  /** Marks a container clean if it has not changed since the supplied revision was saved. */
  @ApiStatus.Internal
  public void markClean(final DataContainerKey<?> key, final long revision) {
    stateUnchecked(key).markClean(revision);
  }

  /** Replaces one persistent value with a fresh default and informs feature containers. */
  @ApiStatus.Internal
  public void reset(final DataContainerKey<?> key) {
    DataContainerKey<?> checkedKey = Objects.requireNonNull(key, "key");
    resetUnchecked(checkedKey);
    for (PlayerContainer container : featureContainers) {
      if (container != null) {
        container.onDataReset(checkedKey);
      }
    }
  }

  private <T> ContainerState<T> state(final DataContainerKey<T> key) {
    return castState(key, stateUnchecked(key));
  }

  private ContainerState<?> stateUnchecked(final DataContainerKey<?> key) {
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
  private <T> ContainerState<T> castState(
      final DataContainerKey<T> key,
      final ContainerState<?> state
  ) {
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
      R result = update.apply(value);
      dirty = true;
      revision++;
      return result;
    }

    private synchronized boolean isDirty() {
      return dirty;
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
  }

  /** Pairs a copied container value with the revision observed while copying it. */
  @Value
  public static class ContainerSnapshot<T> {
    T value;
    long revision;
  }
}
