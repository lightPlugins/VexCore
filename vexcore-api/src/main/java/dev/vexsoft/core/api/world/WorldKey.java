package dev.vexsoft.core.api.world;

import java.util.Locale;
import java.util.Objects;

/** Identifies a Minecraft world through its persistent namespaced key. */
public record WorldKey(String namespace, String value) {

  /** Creates and validates a world key. */
  public WorldKey {
    namespace = normalizeNamespace(namespace);
    value = normalizeValue(value);
  }

  /** Parses a namespaced world key such as {@code minecraft:overworld}. */
  public static WorldKey parse(final String value) {
    String checkedValue = Objects.requireNonNull(value, "value").trim();
    int separator = checkedValue.indexOf(':');
    if (separator <= 0 || separator == checkedValue.length() - 1
        || checkedValue.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException(
          "World ID must use the format namespace:value: " + checkedValue
      );
    }
    return new WorldKey(
        checkedValue.substring(0, separator),
        checkedValue.substring(separator + 1)
    );
  }

  /** Returns the canonical namespaced representation. */
  public String asString() {
    return namespace + ':' + value;
  }

  @Override
  public String toString() {
    return asString();
  }

  private static String normalizeNamespace(final String namespace) {
    String checkedNamespace = Objects.requireNonNull(namespace, "namespace")
        .trim()
        .toLowerCase(Locale.ROOT);
    if (!checkedNamespace.matches("[a-z0-9._-]+")) {
      throw new IllegalArgumentException("Invalid world namespace: " + checkedNamespace);
    }
    return checkedNamespace;
  }

  private static String normalizeValue(final String value) {
    String checkedValue = Objects.requireNonNull(value, "value")
        .trim()
        .toLowerCase(Locale.ROOT);
    if (!checkedValue.matches("[a-z0-9/._-]+")) {
      throw new IllegalArgumentException("Invalid world value: " + checkedValue);
    }
    return checkedValue;
  }
}
