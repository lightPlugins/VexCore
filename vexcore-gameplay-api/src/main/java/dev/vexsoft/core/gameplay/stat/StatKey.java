package dev.vexsoft.core.gameplay.stat;

import java.util.Locale;
import java.util.Objects;

/** Stable, namespaced identity used to persist and register a stat. */
public record StatKey(String namespace, String value) implements Comparable<StatKey> {

  /** Creates and validates a namespaced stat key. */
  public StatKey {
    namespace = validatePart(namespace, "namespace");
    value = validatePart(value, "value");
  }

  /** Creates a stat key from a namespace and value. */
  public static StatKey of(final String namespace, final String value) {
    return new StatKey(namespace, value);
  }

  /** Parses a key formatted as {@code namespace:value}. */
  public static StatKey parse(final String input) {
    String checkedInput = Objects.requireNonNull(input, "input");
    int separator = checkedInput.indexOf(':');
    if (separator <= 0 || separator == checkedInput.length() - 1
        || checkedInput.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException("Stat key must use namespace:value: " + checkedInput);
    }
    return of(checkedInput.substring(0, separator), checkedInput.substring(separator + 1));
  }

  @Override
  public int compareTo(final StatKey other) {
    return toString().compareTo(Objects.requireNonNull(other, "other").toString());
  }

  @Override
  public String toString() {
    return namespace + ':' + value;
  }

  private static String validatePart(final String input, final String name) {
    String normalized = Objects.requireNonNull(input, name)
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid stat " + name + ": " + input);
    }
    return normalized;
  }
}
