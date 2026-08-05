package dev.vexsoft.core.api.localization;

import java.util.Locale;
import java.util.Objects;
import lombok.Value;

/** Identifies a language using a normalized {@code language_COUNTRY} value. */
@Value
public class LanguageKey implements Comparable<LanguageKey> {

  public static final LanguageKey EN_EN = LanguageKey.of("en_EN");

  String value;

  private LanguageKey(final String value) {
    this.value = normalize(value);
  }

  /** Creates a normalized language key from a folder name */
  public static LanguageKey of(final String value) {
    return new LanguageKey(value);
  }

  @Override
  public int compareTo(final LanguageKey other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }

  private static String normalize(final String value) {
    String checked = Objects.requireNonNull(value, "value").trim();
    String[] parts = checked.replace('-', '_').split("_", -1);
    if (parts.length != 2 || !parts[0].matches("[A-Za-z]{2}") || !parts[1].matches("[A-Za-z]{2}")) {
      throw new IllegalArgumentException("Invalid language key: " + value);
    }
    return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
  }
}
