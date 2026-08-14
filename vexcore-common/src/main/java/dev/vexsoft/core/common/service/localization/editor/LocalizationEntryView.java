package dev.vexsoft.core.common.service.localization.editor;

import java.util.Objects;

/** One editable key in a localization YAML file. */
public record LocalizationEntryView(
    String key,
    LocalizationValue value,
    boolean inherited
) {

  public LocalizationEntryView {
    key = Objects.requireNonNull(key, "key");
    value = Objects.requireNonNull(value, "value");
  }
}
