package dev.vexsoft.core.inventory;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Value;

@Value
public class InventoryKey {

  private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9._-]+:[a-z0-9._/-]+");

  String value;

  public InventoryKey(final String value) {
    String normalized = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    if (!VALID_KEY.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid inventory key: " + value);
    }
    this.value = normalized;
  }

  /** Creates a validated inventory key from its namespaced value */
  public static InventoryKey of(final String value) {
    return new InventoryKey(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
