package dev.vexsoft.core.currency;

import java.util.Locale;
import java.util.Objects;

/** Stable namespaced identity used to register and persist a virtual currency. */
public record CurrencyKey(String namespace, String value) implements Comparable<CurrencyKey> {

  /** Validates and normalizes both key parts. */
  public CurrencyKey {
    namespace = validateNamespace(namespace);
    value = validateValue(value);
  }

  /** Creates a currency key from its namespace and value. */
  public static CurrencyKey of(final String namespace, final String value) {
    return new CurrencyKey(namespace, value);
  }

  /** Parses a key formatted as {@code namespace:value}. */
  public static CurrencyKey parse(final String input) {
    String checked = Objects.requireNonNull(input, "input").trim();
    int separator = checked.indexOf(':');
    if (separator <= 0 || separator == checked.length() - 1
        || checked.indexOf(':', separator + 1) >= 0) {
      throw new IllegalArgumentException("Currency key must use namespace:value: " + input);
    }
    return of(checked.substring(0, separator), checked.substring(separator + 1));
  }

  @Override
  public int compareTo(final CurrencyKey other) {
    return toString().compareTo(Objects.requireNonNull(other, "other").toString());
  }

  @Override
  public String toString() {
    return namespace + ':' + value;
  }

  private static String validateNamespace(final String input) {
    String normalized = Objects.requireNonNull(input, "namespace")
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid currency namespace: " + input);
    }
    return normalized;
  }

  private static String validateValue(final String input) {
    String normalized = Objects.requireNonNull(input, "value").trim().toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z][a-z0-9_-]{0,62}")) {
      throw new IllegalArgumentException("Invalid currency value: " + input);
    }
    return normalized;
  }
}
