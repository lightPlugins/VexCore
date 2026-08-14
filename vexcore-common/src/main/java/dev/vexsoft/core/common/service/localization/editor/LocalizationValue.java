package dev.vexsoft.core.common.service.localization.editor;

import java.util.List;
import java.util.Objects;

/** Raw editable localization value. */
public record LocalizationValue(List<String> lines, boolean list) {

  public LocalizationValue {
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("Localization value must contain at least one line");
    }
  }

  public static LocalizationValue text(final String value) {
    return new LocalizationValue(List.of(Objects.requireNonNull(value, "value")), false);
  }

  public static LocalizationValue lines(final List<String> values) {
    return new LocalizationValue(values, true);
  }
}
