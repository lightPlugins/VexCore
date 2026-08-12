package dev.vexsoft.core.execution;

import java.util.Map;
import net.kyori.adventure.text.Component;

/** Structured, localization-ready description emitted by one compiled execution entry. */
public record ExecutionDescription(
    String state,
    Map<String, Component> placeholders,
    Component fallback
) {

  /** Copies placeholder values. */
  public ExecutionDescription {
    state = state == null ? "" : state;
    placeholders = Map.copyOf(placeholders);
  }
}
