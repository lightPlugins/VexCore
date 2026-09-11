package dev.vexsoft.core.text;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

/** Converts embedded newlines to independent lore rows without losing inherited styles. */
public final class ComponentLines {

  private ComponentLines() {
  }

  /** Splits textual newlines, preserving decorations, fonts, and interaction events. */
  public static List<Component> split(final Component component) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.empty());
    append(component, Style.empty(), lines);
    return List.copyOf(lines);
  }

  private static void append(
      final Component component,
      final Style inheritedStyle,
      final List<Component> lines
  ) {
    Style style = component.style().merge(
        inheritedStyle,
        Style.Merge.Strategy.IF_ABSENT_ON_TARGET
    );
    if (component instanceof TextComponent text) {
      String[] parts = text.content().split("\n", -1);
      for (int index = 0; index < parts.length; index++) {
        if (index > 0) {
          lines.add(Component.empty());
        }
        int last = lines.size() - 1;
        lines.set(last, lines.get(last).append(Component.text(parts[index], style)));
      }
    } else {
      int last = lines.size() - 1;
      lines.set(last, lines.get(last).append(component.children(List.of()).style(style)));
    }
    for (Component child : component.children()) {
      append(child, style, lines);
    }
  }
}
