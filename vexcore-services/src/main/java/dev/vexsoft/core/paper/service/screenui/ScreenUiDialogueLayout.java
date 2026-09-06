package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.paper.screenui.DialoguePage;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextOverflow;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.BreakIterator;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/** Bounded immutable layout preparation, independent of player/task lifetime. */
public final class ScreenUiDialogueLayout {
  private ScreenUiDialogueLayout() {
  }

  public static void validate(DialoguePanelLayout layout, ScreenUiVersionDefinition version) {
    ScreenUiRenderer.texture(UiTexture.PANEL, TextureLayout.builder().anchor(layout.anchor())
        .x(layout.panelX()).y(layout.panelY()).build(), version);
    version.textFont(layout.anchor(), layout.nameY());
    version.textFont(layout.anchor(), layout.hintY());
    for (int i = 0; i < layout.linesPerPage(); i++) {
      version.textFont(layout.anchor(), layout.bodyY() + i * (12 + layout.lineSpacing()));
    }
  }

  public static PreparedDialogue prepare(Component speaker, List<Component> paragraphs,
      DialoguePanelLayout layout, ScreenUiVersionDefinition version) {
    validate(layout, version);
    if (paragraphs.isEmpty() || paragraphs.size() > 128) {
      throw new IllegalArgumentException("Dialogue requires 1..128 paragraphs");
    }
    int[] budget = {16384};
    Component normalizedName = normalize(speaker, true, budget, 0);
    var nameLines = ScreenUiRenderer.layout(List.of(normalizedName), TextBlockLayout.builder()
        .maxWidth(layout.textWidth()).overflow(TextOverflow.ELLIPSIS).build());
    if (nameLines.size() != 1) {
      throw new IllegalArgumentException("Dialogue speaker must occupy one line");
    }
    Component name = line(nameLines.getFirst().glyphs());
    List<DialoguePage> pages = new ArrayList<>();
    for (Component paragraph : List.copyOf(paragraphs)) {
      for (Component sentence : sentences(normalize(paragraph, false, budget, 0))) {
      var lines = ScreenUiRenderer.wrapParagraph(sentence, layout.textWidth());
      for (int start = 0; start < lines.size(); start += layout.linesPerPage()) {
        if (pages.size() == 128) {
          throw new IllegalArgumentException("Dialogue exceeds 128 visual pages");
        }
        List<Component> content = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        for (var glyphs : lines.subList(start, Math.min(lines.size(), start + layout.linesPerPage()))) {
          content.add(line(glyphs));
          for (var glyph : glyphs) {
            plain.append(glyph.text());
          }
        }
        pages.add(new DialoguePage(content, plain.codePointCount(0, plain.length()), plain.toString()));
      }
      }
    }
    return new PreparedDialogue(name, pages, layout);
  }

  private static List<Component> sentences(Component paragraph) {
    String plain = PlainTextComponentSerializer.plainText().serialize(paragraph);
    if (plain.isEmpty()) {
      return List.of(paragraph);
    }
    BreakIterator boundaries = BreakIterator.getSentenceInstance(Locale.ROOT);
    boundaries.setText(plain);
    List<Component> result = new ArrayList<>();
    int start = boundaries.first();
    for (int end = boundaries.next(); end != BreakIterator.DONE; start = end, end = boundaries.next()) {
      int from = start;
      while (from < end && Character.isWhitespace(plain.charAt(from))) {
        from++;
      }
      if (from < end) {
        result.add(slice(paragraph, from, end, new int[]{0}));
      }
    }
    return result.isEmpty() ? List.of(Component.empty()) : result;
  }

  private static Component slice(Component component, int start, int end, int[] position) {
    TextComponent text = (TextComponent) component;
    int offset = position[0];
    position[0] += text.content().length();
    int from = Math.clamp(start - offset, 0, text.content().length());
    int to = Math.clamp(end - offset, from, text.content().length());
    List<Component> children = new ArrayList<>();
    for (Component child : component.children()) {
      children.add(slice(child, start, end, position));
    }
    return text.content(text.content().substring(from, to)).children(children);
  }

  private static Component line(List<ScreenUiRenderer.Glyph> glyphs) {
    var builder = Component.text();
    for (var glyph : glyphs) {
      builder.append(Component.text(glyph.text()).style(glyph.style()));
    }
    return builder.build();
  }

  private static Component normalize(Component component, boolean bold, int[] budget, int depth) {
    if (!(component instanceof TextComponent text) || depth > 32 || --budget[0] < 0) {
      throw new IllegalArgumentException("Invalid dialogue component tree");
    }
    budget[0] -= text.content().codePointCount(0, text.content().length());
    if (budget[0] < 0) {
      throw new IllegalArgumentException("Dialogue exceeds character budget");
    }
    List<Component> children = new ArrayList<>();
    for (Component child : component.children()) {
      children.add(normalize(child, bold, budget, depth + 1));
    }
    return component.children(children).decoration(TextDecoration.BOLD, bold)
        .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.UNDERLINED, false)
        .decoration(TextDecoration.STRIKETHROUGH, false);
  }
}
