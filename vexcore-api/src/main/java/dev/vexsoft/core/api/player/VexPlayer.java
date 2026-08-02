package dev.vexsoft.core.api.player;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.Value;
import org.jetbrains.annotations.ApiStatus;

public final class VexPlayer {

  @Getter
  private final UUID uniqueId;
  @Getter
  private volatile String name;
  private final Map<DataContainerKey<?>, ContainerState<?>> containers = new ConcurrentHashMap<>();

  @ApiStatus.Internal
  public VexPlayer(final UUID uniqueId, final String name) {
    this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    this.name = Objects.requireNonNull(name, "name");
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

  @ApiStatus.Internal
  public <T> void install(final DataContainerKey<T> key, final T value) {
    install(key, value, false);
  }

  @ApiStatus.Internal
  public <T> void install(final DataContainerKey<T> key, final T value, final boolean dirty) {
    Objects.requireNonNull(key, "key");
    T checkedValue = key.getType().cast(Objects.requireNonNull(value, "value"));
    containers.putIfAbsent(key, new ContainerState<>(checkedValue, dirty));
  }

  @ApiStatus.Internal
  public void setName(final String name) {
    this.name = Objects.requireNonNull(name, "name");
  }

  @ApiStatus.Internal
  public Set<DataContainerKey<?>> getDirtyKeys() {
    return containers.entrySet().stream()
        .filter(entry -> entry.getValue().isDirty())
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  @ApiStatus.Internal
  public Object getValue(final DataContainerKey<?> key) {
    return stateUnchecked(key).read();
  }

  @ApiStatus.Internal
  public <R> ContainerSnapshot<R> snapshot(
      final DataContainerKey<?> key,
      final Function<Object, R> snapshotter
  ) {
    return stateUnchecked(key).snapshot(Objects.requireNonNull(snapshotter, "snapshotter"));
  }

  @ApiStatus.Internal
  public void markClean(final DataContainerKey<?> key, final long revision) {
    stateUnchecked(key).markClean(revision);
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
  private <T> ContainerState<T> castState(
      final DataContainerKey<T> key,
      final ContainerState<?> state
  ) {
    key.getType().cast(state.read());
    return (ContainerState<T>) state;
  }

  private static final class ContainerState<T> {

    private final T value;
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
  }

  @Value
  public static class ContainerSnapshot<T> {
    T value;
    long revision;
  }
}
