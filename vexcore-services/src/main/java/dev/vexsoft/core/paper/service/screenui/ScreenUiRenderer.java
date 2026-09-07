package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.paper.screenui.HorizontalAlignment;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextOverflow;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiAnimation;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.VerticalAlignment;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Pure bounded layout compiler for the VexCore bitmap font contract. */
public final class ScreenUiRenderer {
  private ScreenUiRenderer() {}

  public record Glyph(String text, Style style, int width) {}

  public record Line(List<Glyph> glyphs, int width, int x, int y) {}

  public static List<Line> layout(final List<Component> input, final TextBlockLayout layout) {
    Objects.requireNonNull(layout, "layout");
    List<Component> copy = List.copyOf(input);
    if (copy.size() > 32) {
      throw new IllegalArgumentException("At most 32 input lines are supported");
    }

    List<List<Glyph>> paragraphs = new ArrayList<>();
    int[] budget = {4096};
    for (Component component : copy) {
      paragraphs.add(new ArrayList<>());
      flatten(component, Style.empty(), paragraphs, budget, 0);
    }

    List<List<Glyph>> wrapped = new ArrayList<>();
    for (List<Glyph> paragraph : paragraphs) {
      wrap(paragraph, layout, wrapped);
    }

    if (wrapped.size() > 32) {
      throw new IllegalArgumentException("Text exceeds 32 visual lines");
    }

    int height =
        wrapped.isEmpty()
            ? 0
            : wrapped.size() * (ScreenUiFont.HEIGHT + layout.lineSpacing()) - layout.lineSpacing();
    int top = top(layout.y(), height, layout.verticalAlignment());
    if (layout.anchor() == null && (top < 0 || top + height > 250)) {
      throw new IllegalArgumentException("Text exceeds vertical UI bounds");
    }

    List<Line> result = new ArrayList<>();
    for (int i = 0; i < wrapped.size(); i++) {
      List<Glyph> glyphs = List.copyOf(wrapped.get(i));
      int width = width(glyphs);
      int y = top + i * (ScreenUiFont.HEIGHT + layout.lineSpacing());
      if (layout.anchor() == null ? y > 240 : y < -256 || y > 256) {
        throw new IllegalArgumentException("Unsupported text baseline");
      }

      result.add(new Line(glyphs, width, left(layout.x(), width, layout.horizontalAlignment()), y));
    }

    return List.copyOf(result);
  }

  private static void flatten(
      Component component, Style inherited, List<List<Glyph>> lines, int[] budget, int depth) {
    if (--budget[0] < 0 || depth > 32) {
      throw new IllegalArgumentException("UI component tree is too large");
    }

    if (!(component instanceof TextComponent text)) {
      throw new IllegalArgumentException(
          "Resolve translations and non-text components before displaying UI");
    }

    Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
    boolean icon = style.font() != null && style.font().value().startsWith("icon/");
    if (style.font() != null && !style.font().equals(Key.key("minecraft:default")) && !icon) {
      throw new IllegalArgumentException(
          "Prototype text blocks require the built-in UI font; use textureBlock for icons");
    }

    if (style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) {
      throw new IllegalArgumentException("Obfuscated UI text has no stable metrics");
    }

    style = style.clickEvent(null).hoverEvent(null);
    for (int point : text.content().codePoints().toArray()) {
      if (--budget[0] < 0) {
        throw new IllegalArgumentException("UI text exceeds its character budget");
      }

      if (point == '\n') {
        lines.add(new ArrayList<>());
        continue;
      }

      if (!(icon
          ? point == 0xE001
          : point >= 32 && point <= 126
              || point >= 160 && point <= 255
              || point == 0x25A0
              || point == 0x25A1)) {
        throw new IllegalArgumentException("Unsupported UI font code point: " + point);
      }

      int width = icon ? 13 : ScreenUiFont.advance(point);
      if (icon) {
        style = style.decoration(TextDecoration.BOLD, false);
      }
      if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
        width++;
      }

      lines.getLast().add(new Glyph(Character.toString(point), style, width));
    }

    for (Component child : component.children()) {
      flatten(child, style, lines, budget, depth + 1);
    }
  }

  static List<List<Glyph>> wrapParagraph(Component component, int maxWidth) {
    List<List<Glyph>> paragraphs = new ArrayList<>();
    paragraphs.add(new ArrayList<>());
    flatten(component, Style.empty(), paragraphs, new int[] {4096}, 0);
    List<List<Glyph>> result = new ArrayList<>();
    TextBlockLayout layout = TextBlockLayout.builder().maxWidth(maxWidth).build();
    for (List<Glyph> paragraph : paragraphs) {
      wrap(paragraph, layout, result);
    }
    if (result.size() > 32) {
      throw new IllegalArgumentException("Dialogue paragraph exceeds 32 lines");
    }
    return result;
  }

  private static void wrap(
      List<Glyph> paragraph, TextBlockLayout layout, List<List<Glyph>> output) {
    List<Glyph> remaining = new ArrayList<>(paragraph);
    if (remaining.isEmpty()) {
      output.add(List.of());
      return;
    }

    while (!remaining.isEmpty()) {
      if (output.size() >= 32) {
        throw new IllegalArgumentException("Text exceeds 32 visual lines");
      }

      int width = 0;
      int end = 0;
      int lastSpace = -1;
      while (end < remaining.size() && width + remaining.get(end).width() <= layout.maxWidth()) {
        Glyph glyph = remaining.get(end);
        width += glyph.width();
        if (glyph.text().equals(" ")) {
          lastSpace = end;
        }

        end++;
      }

      if (end == remaining.size()) {
        output.add(List.copyOf(remaining));
        return;
      }

      if (layout.overflow() == TextOverflow.REJECT || end == 0) {
        throw new IllegalArgumentException("UI text exceeds maximum width");
      }

      if (layout.overflow() == TextOverflow.ELLIPSIS) {
        Glyph dot =
            new Glyph(
                ".",
                remaining.getFirst().style(),
                ScreenUiFont.advance('.')
                    + (remaining.getFirst().style().decoration(TextDecoration.BOLD)
                            == TextDecoration.State.TRUE
                        ? 1
                        : 0));
        int dots = 3 * dot.width();
        if (dots > layout.maxWidth()) {
          throw new IllegalArgumentException("Width cannot fit an ellipsis");
        }

        while (end > 0 && width + dots > layout.maxWidth()) {
          width -= remaining.get(--end).width();
        }

        List<Glyph> clipped = new ArrayList<>(remaining.subList(0, end));
        clipped.addAll(List.of(dot, dot, dot));
        output.add(clipped);
        return;
      }

      int take = lastSpace > 0 ? lastSpace : end;
      output.add(List.copyOf(remaining.subList(0, take)));
      remaining.subList(0, lastSpace > 0 ? lastSpace + 1 : end).clear();
    }
  }

  private static int width(List<Glyph> glyphs) {
    return glyphs.stream().mapToInt(Glyph::width).sum();
  }

  private static int left(int x, int width, HorizontalAlignment alignment) {
    return x
        - switch (alignment) {
          case LEFT -> 0;
          case CENTER -> width / 2;
          case RIGHT -> width;
        };
  }

  private static int top(int y, int height, VerticalAlignment alignment) {
    return y
        - switch (alignment) {
          case TOP -> 0;
          case MIDDLE -> height / 2;
          case BOTTOM -> height;
        };
  }

  public static Component text(List<Component> input, TextBlockLayout layout) {
    return text(input, layout, null);
  }

  public static Component text(
      List<Component> input, TextBlockLayout layout, ScreenUiVersionDefinition version) {
    var output = Component.text();
    for (Line line : layout(input, layout)) {
      Key font =
          layout.anchor() == null
              ? Key.key("vexcore:ui/text/y_" + line.y())
              : Objects.requireNonNull(version, "Anchored UI requires a version adapter")
                  .textFont(layout.anchor(), line.y(), layout.animation() != null, layout.scale());
      int transport = layout.anchor() == null ? 0 : version.horizontalTransport(line.y());
      output.append(space(line.x() + transport));
      StringBuilder run = new StringBuilder();
      Style current = null;
      for (Glyph glyph : line.glyphs()) {
        if (layout.anchor() != null) {
          for (TextDecoration decoration :
              List.of(
                  TextDecoration.ITALIC, TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH)) {
            if (glyph.style().decoration(decoration) == TextDecoration.State.TRUE) {
              throw new IllegalArgumentException(
                  "Anchored prototype does not support " + decoration);
            }
          }
        }
        if (current != null && !current.equals(glyph.style())) {
          output.append(
              textRun(
                  run,
                  animatedStyle(current, layout.animation()),
                  runFont(current, font, layout, line.y(), version),
                  layout.anchor() != null));
          run.setLength(0);
        }

        current = glyph.style();
        run.append(glyph.text());
      }

      if (current != null) {
        output.append(
            textRun(
                run,
                animatedStyle(current, layout.animation()),
                runFont(current, font, layout, line.y(), version),
                layout.anchor() != null));
      }

      output.append(space(-line.x() - transport - line.width()));
    }

    return output.build().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
  }

  public static Component texture(UiTexture texture, TextureLayout layout) {
    return texture(texture, layout, null);
  }

  private static Component textRun(StringBuilder text, Style style, Key font, boolean anchored) {
    Component result = Component.text(text.toString()).style(style.font(font));
    return anchored ? result.shadowColor(ShadowColor.none()) : result;
  }

  private static Style animatedStyle(Style style, UiAnimation animation) {
    if (animation == null) {
      return style;
    }
    var color = style.color() == null ? NamedTextColor.WHITE : style.color();
    int rgb =
        ((color.red() * 7 + 127) / 255 << 6)
            | ((color.green() * 7 + 127) / 255 << 3)
            | (color.blue() * 7 + 127) / 255;
    return style.color(TextColor.color((rgb << 15) | animation.startTick()));
  }

  private static Key runFont(
      Style style, Key fallback, TextBlockLayout layout, int y, ScreenUiVersionDefinition version) {
    if (style.font() == null || !style.font().value().startsWith("icon/")) {
      return fallback;
    }
    if (layout.anchor() == null) {
      throw new IllegalArgumentException("UI icons require a screen anchor");
    }
    return version.iconFont(
        style.font(), layout.anchor(), y, layout.animation() != null, layout.scale());
  }

  public static Component texture(
      UiTexture texture, TextureLayout layout, ScreenUiVersionDefinition version) {
    int x = left(layout.x(), texture.width(), layout.horizontalAlignment());
    int y = top(layout.y(), texture.height(), layout.verticalAlignment());
    if (layout.anchor() == null && (y < 0 || y > 240 || y + texture.height() > 250)) {
      throw new IllegalArgumentException("Texture exceeds vertical UI bounds");
    }

    Key font =
        layout.anchor() == null
            ? Key.key(texture.fontPrefix().namespace(), texture.fontPrefix().value() + "/y_" + y)
            : Objects.requireNonNull(version, "Anchored UI requires a version adapter")
                .textureFont(
                    texture, layout.anchor(), y, layout.animation() != null, layout.scale());
    Component glyph =
        Component.text(Character.toString(texture.codePoint()))
            .style(animatedStyle(Style.style(layout.tint()), layout.animation()).font(font));
    if (layout.anchor() != null) {
      glyph = glyph.shadowColor(ShadowColor.none());
      x += version.horizontalTransport(y);
    }
    x += texture.glyphOffsetX();
    return Component.empty()
        .append(space(x))
        .append(glyph)
        .append(space(-x - texture.glyphAdvance()));
  }

  public static Component space(int advance) {
    if (Math.abs((long) advance) > 2097151) {
      throw new IllegalArgumentException("UI offset out of range");
    }

    StringBuilder text = new StringBuilder();
    int amount = Math.abs(advance);
    for (int bit = 0; amount != 0; bit++, amount >>>= 1) {
      if ((amount & 1) != 0) {
        text.appendCodePoint((advance < 0 ? 0xE020 : 0xE000) + bit);
      }
    }

    return Component.text(text.toString())
        .font(Key.key("vexcore:ui/space"))
        .decoration(TextDecoration.BOLD, false)
        .decoration(TextDecoration.ITALIC, false);
  }
}
