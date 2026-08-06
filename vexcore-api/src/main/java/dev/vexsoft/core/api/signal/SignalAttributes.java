package dev.vexsoft.core.api.signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;

/**
 * Stores a small immutable collection of strongly constrained signal attributes.
 *
 * <p>Values may be strings, longs, doubles, booleans, UUIDs, or Adventure keys. The compact
 * array-backed representation avoids a map allocation for the small payloads signals commonly
 * carry.</p>
 */
public final class SignalAttributes {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]*");
  private static final SignalAttributes EMPTY = new SignalAttributes(new String[0], new Object[0]);

  private final String[] names;
  private final Object[] values;

  private SignalAttributes(final String[] names, final Object[] values) {
    this.names = names;
    this.values = values;
  }

  /** Returns the shared empty attribute collection. */
  public static SignalAttributes empty() {
    return EMPTY;
  }

  /** Creates a builder for an immutable attribute collection. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the number of stored attributes. */
  public int size() {
    return names.length;
  }

  /** Returns whether no attributes are stored. */
  public boolean isEmpty() {
    return names.length == 0;
  }

  /** Finds an attribute without converting its supported value type. */
  public Optional<Object> find(String name) {
    int index = indexOf(normalizeName(name));
    return index < 0 ? Optional.empty() : Optional.of(values[index]);
  }

  /** Finds a string attribute. */
  public Optional<String> findString(String name) {
    return findTyped(name, String.class);
  }

  /** Finds a long attribute. */
  public Optional<Long> findLong(String name) {
    return findTyped(name, Long.class);
  }

  /** Finds a double attribute. */
  public Optional<Double> findDouble(String name) {
    return findTyped(name, Double.class);
  }

  /** Finds a boolean attribute. */
  public Optional<Boolean> findBoolean(String name) {
    return findTyped(name, Boolean.class);
  }

  /** Finds a UUID attribute. */
  public Optional<UUID> findUuid(String name) {
    return findTyped(name, UUID.class);
  }

  /** Finds an Adventure key attribute. */
  public Optional<Key> findKey(String name) {
    return findTyped(name, Key.class);
  }

  private <T> Optional<T> findTyped(final String name, final Class<T> type) {
    return find(name).filter(type::isInstance).map(type::cast);
  }

  private int indexOf(final String name) {
    for (int index = 0; index < names.length; index++) {
      if (names[index].equals(name)) {
        return index;
      }
    }
    return -1;
  }

  private static String normalizeName(final String name) {
    String normalized = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
    if (!NAME.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid signal attribute name: " + name);
    }
    return normalized;
  }

  /**
   * Builds a compact immutable signal attribute collection.
   */
  public static final class Builder {

    private final List<String> names = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();

    private Builder() {
    }

    /** Adds a string attribute. */
    public Builder putString(final String name, final String value) {
      return put(name, Objects.requireNonNull(value, "value"));
    }

    /** Adds a long attribute. */
    public Builder putLong(final String name, final long value) {
      return put(name, value);
    }

    /** Adds a double attribute. */
    public Builder putDouble(final String name, final double value) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Signal attribute double must be finite");
      }
      return put(name, value);
    }

    /** Adds a boolean attribute. */
    public Builder putBoolean(final String name, final boolean value) {
      return put(name, value);
    }

    /** Adds a UUID attribute. */
    public Builder putUuid(final String name, final UUID value) {
      return put(name, Objects.requireNonNull(value, "value"));
    }

    /** Adds an Adventure key attribute. */
    public Builder putKey(final String name, final Key value) {
      return put(name, Objects.requireNonNull(value, "value"));
    }

    /** Creates the immutable attribute collection. */
    public SignalAttributes build() {
      if (names.isEmpty()) {
        return EMPTY;
      }
      return new SignalAttributes(names.toArray(String[]::new), values.toArray());
    }

    private Builder put(final String rawName, final Object value) {
      String name = normalizeName(rawName);
      if (names.contains(name)) {
        throw new IllegalArgumentException("Duplicate signal attribute: " + name);
      }
      names.add(name);
      values.add(value);
      return this;
    }
  }
}
