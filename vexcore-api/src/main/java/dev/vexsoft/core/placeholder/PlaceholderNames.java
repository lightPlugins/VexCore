package dev.vexsoft.core.placeholder;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/** Validates stable placeholder namespaces and identifiers. */
@UtilityClass
public final class PlaceholderNames {

  private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

  /** Normalizes a plugin name into its PlaceholderAPI-compatible namespace. */
  public static String namespace(final String value) {
    return normalize(value, "plugin namespace", true);
  }

  /** Validates and normalizes one owner-local placeholder identifier. */
  public static String identifier(final String value) {
    return normalize(value, "placeholder id", false);
  }

  private static String normalize(
      final String value,
      final String role,
      final boolean compact
  ) {
    String normalized = Objects.requireNonNull(value, role).trim().toLowerCase(Locale.ROOT);
    if (compact) {
      normalized = normalized.replace("-", "").replace(" ", "");
    }
    if (!IDENTIFIER.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid " + role + ": " + value);
    }
    return normalized;
  }
}
