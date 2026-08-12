package dev.vexsoft.core.paper.level.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/** Expands the three mandatory structural lore placeholders into localized multiline blocks. */
public final class LevelLoreRenderer {

  public static final String REWARDS = "%rewards%";
  public static final String COSTS = "%costs%";
  public static final String REQUIREMENTS = "%requirements%";
  private static final List<String> REQUIRED = List.of(REWARDS, COSTS, REQUIREMENTS);

  private LevelLoreRenderer() {}

  /** Expands each placeholder line and rejects language templates missing a structural block. */
  public static List<Component> render(
      final List<Component> localizedTemplate,
      final List<Component> rewards,
      final List<Component> costs,
      final List<Component> requirements
  ) {
    Map<String, List<Component>> blocks = Map.of(
        REWARDS, List.copyOf(rewards),
        COSTS, List.copyOf(costs),
        REQUIREMENTS, List.copyOf(requirements)
    );
    List<String> found = new ArrayList<>();
    List<Component> result = new ArrayList<>();
    for (Component line : Objects.requireNonNull(localizedTemplate, "localizedTemplate")) {
      String text = plainText(line);
      List<Component> replacement = blocks.get(text);
      if (replacement == null) {
        result.add(line);
      } else {
        found.add(text);
        result.addAll(replacement);
      }
    }
    for (String placeholder : REQUIRED) {
      if (!found.contains(placeholder)) {
        throw new IllegalArgumentException(
            "Localized level lore must contain a dedicated " + placeholder + " line"
        );
      }
    }
    return List.copyOf(result);
  }

  private static String plainText(final Component component) {
    StringBuilder result = new StringBuilder();
    append(component, result);
    return result.toString();
  }

  private static void append(final Component component, final StringBuilder result) {
    if (component instanceof TextComponent text) {
      result.append(text.content());
    }
    component.children().forEach(child -> append(child, result));
  }
}
