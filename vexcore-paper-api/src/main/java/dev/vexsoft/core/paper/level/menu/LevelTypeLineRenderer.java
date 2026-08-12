package dev.vexsoft.core.paper.level.menu;

import dev.vexsoft.core.execution.TypedExecutionDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Applies localized per-type line templates to structured execution descriptions. */
public final class LevelTypeLineRenderer {

  private LevelTypeLineRenderer() {}

  /**
   * Renders descriptions with {@code type.state} templates taking precedence over {@code type}.
   * Missing templates use the execution type's fallback Component.
   */
  public static List<Component> render(
      final Map<String, Component> localizedTemplates,
      final List<TypedExecutionDescription> descriptions
  ) {
    Objects.requireNonNull(localizedTemplates, "localizedTemplates");
    List<Component> result = new ArrayList<>();
    for (TypedExecutionDescription description : descriptions) {
      Component template = null;
      if (!description.state().isBlank()) {
        template = localizedTemplates.get(description.type() + '.' + description.state());
      }
      if (template == null) {
        template = localizedTemplates.get(description.type());
      }
      if (template == null) {
        result.add(description.fallback());
        continue;
      }
      Component rendered = template;
      for (Map.Entry<String, Component> placeholder : description.placeholders().entrySet()) {
        rendered = rendered.replaceText(builder -> builder
            .matchLiteral('%' + placeholder.getKey() + '%')
            .replacement(placeholder.getValue()));
      }
      result.add(rendered);
    }
    return List.copyOf(result);
  }
}
