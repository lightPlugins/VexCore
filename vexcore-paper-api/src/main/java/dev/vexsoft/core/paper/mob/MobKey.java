package dev.vexsoft.core.paper.mob;

import java.util.Locale;
import java.util.Objects;

/** Stable namespaced identity for a custom mob definition. */
public record MobKey(String namespace, String value) implements Comparable<MobKey> {

  /** Creates and validates a mob key. */
  public MobKey {
    namespace = validate(namespace, "namespace");
    value = validate(value, "value");
  }

  /** Creates a mob key from its namespace and value. */
  public static MobKey of(final String namespace, final String value) {
    return new MobKey(namespace, value);
  }

  /** Parses a key formatted as {@code namespace:value}. */
  public static MobKey parse(final String input) {
    String checked = Objects.requireNonNull(input, "input").trim();
    int separator = checked.indexOf(':');
    if (separator <= 0 || separator == checked.length() - 1
        || checked.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException("Mob key must use namespace:value: " + checked);
    }
    return of(checked.substring(0, separator), checked.substring(separator + 1));
  }

  @Override
  public int compareTo(final MobKey other) {
    return toString().compareTo(Objects.requireNonNull(other, "other").toString());
  }

  @Override
  public String toString() {
    return namespace + ':' + value;
  }

  private static String validate(final String input, final String name) {
    String normalized = Objects.requireNonNull(input, name)
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_./]{0,126}")) {
      throw new IllegalArgumentException("Invalid mob " + name + ": " + input);
    }
    return normalized;
  }
}
