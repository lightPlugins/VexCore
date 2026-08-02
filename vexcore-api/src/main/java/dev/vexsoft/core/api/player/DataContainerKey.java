package dev.vexsoft.core.api.player;

import java.util.Objects;
import java.util.function.Supplier;
import lombok.Getter;

public final class DataContainerKey<T> {

  @Getter
  private final String name;
  @Getter
  private final Class<T> type;
  private final Supplier<? extends T> defaultValue;

  private DataContainerKey(
      final String name,
      final Class<T> type,
      final Supplier<? extends T> defaultValue
  ) {
    this.name = validateName(name);
    this.type = Objects.requireNonNull(type, "type");
    this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
  }

  /** Creates a typed key for a player data container */
  public static <T> DataContainerKey<T> of(
      final String name,
      final Class<T> type,
      final Supplier<? extends T> defaultValue
  ) {
    return new DataContainerKey<>(name, type, defaultValue);
  }

  /** Creates a fresh default value for this container */
  public T createDefaultValue() {
    return type.cast(Objects.requireNonNull(
        defaultValue.get(),
        "Default value supplier returned null for " + name
    ));
  }

  private static String validateName(final String name) {
    String checkedName = Objects.requireNonNull(name, "name");
    if (!checkedName.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException(
          "Container name must use lower-case SQL-safe characters: " + checkedName
      );
    }
    return checkedName;
  }
}
