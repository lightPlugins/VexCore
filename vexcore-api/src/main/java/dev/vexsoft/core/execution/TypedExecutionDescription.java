package dev.vexsoft.core.execution;

import java.util.Map;
import net.kyori.adventure.text.Component;

/** Structured description associated with its registered reward, cost, or requirement key. */
public record TypedExecutionDescription(
    String type,
    String state,
    Map<String, Component> placeholders,
    Component fallback
) {

  /** Creates a typed description from a compiled entry's description. */
  public static TypedExecutionDescription of(
      final String type,
      final ExecutionDescription description
  ) {
    return new TypedExecutionDescription(
        type, description.state(), description.placeholders(), description.fallback()
    );
  }

  /** Copies placeholder values. */
  public TypedExecutionDescription {
    placeholders = Map.copyOf(placeholders);
  }
}
