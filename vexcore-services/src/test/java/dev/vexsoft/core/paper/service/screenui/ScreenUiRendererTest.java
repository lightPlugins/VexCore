package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.*;
import dev.vexsoft.core.paper.screenui.*;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

public final class ScreenUiRendererTest {
  @Test public void usesProportionalM5x7MetricsAndSupportsGermanText() {
    var lines = ScreenUiRenderer.layout(List.of(Component.text("Wi. \u00c4\u00d6\u00dc\u00e4\u00f6\u00fc\u00df")),
        TextBlockLayout.builder().build());
    assertEquals(List.of(8, 3, 2, 5, 6, 6, 6, 6, 6, 6, 6),
        lines.getFirst().glyphs().stream().map(ScreenUiRenderer.Glyph::width).toList());
  }
  @Test public void centersEachLineWithoutDependingOnOtherLineLengths() {
    var lines = ScreenUiRenderer.layout(List.of(Component.text("ABC"), Component.text("A")),
        TextBlockLayout.builder().x(10).y(20).horizontalAlignment(HorizontalAlignment.CENTER).lineSpacing(2).build());
    assertEquals(1, lines.get(0).x());
    assertEquals(7, lines.get(1).x());
    assertEquals(34, lines.get(1).y());
  }

  @Test public void wrapsStyledComponentsAtWordsAndPreservesBlankLines() {
    Component content = Component.text("AB ", NamedTextColor.RED)
        .append(Component.text("CD", NamedTextColor.GREEN)).append(Component.text("\n\nE"));
    var lines = ScreenUiRenderer.layout(List.of(content), TextBlockLayout.builder().maxWidth(18).build());
    assertEquals(4, lines.size());
    assertEquals("AB", text(lines.get(0)));
    assertEquals("CD", text(lines.get(1)));
    assertEquals(NamedTextColor.GREEN, lines.get(1).glyphs().getFirst().style().color());
    assertEquals("", text(lines.get(2)));
    assertEquals("E", text(lines.get(3)));
  }

  @Test public void inheritedBoldChangesWidthAndBottomAlignmentUsesEntireBlock() {
    Component content = Component.empty().decorate(TextDecoration.BOLD).append(Component.text("AB"));
    var lines = ScreenUiRenderer.layout(List.of(content, Component.text("C")), TextBlockLayout.builder()
        .x(50).y(60).horizontalAlignment(HorizontalAlignment.RIGHT).verticalAlignment(VerticalAlignment.BOTTOM)
        .lineSpacing(2).build());
    assertEquals(14, lines.getFirst().width());
    assertEquals(36, lines.getFirst().x());
    assertEquals(34, lines.getFirst().y());
    assertEquals(48, lines.getLast().y());
  }

  @Test public void overflowAndUnsupportedContentFailBeforePublishing() {
    assertThrows(IllegalArgumentException.class, () -> ScreenUiRenderer.text(List.of(Component.text("ABCD")),
        TextBlockLayout.builder().maxWidth(18).overflow(TextOverflow.REJECT).build()));
    assertThrows(IllegalArgumentException.class, () -> ScreenUiRenderer.text(List.of(Component.text("😀")),
        TextBlockLayout.builder().build()));
    assertThrows(IllegalArgumentException.class, () -> ScreenUiRenderer.text(List.of(Component.translatable("test")),
        TextBlockLayout.builder().build()));
    assertThrows(IllegalArgumentException.class, () -> ScreenUiRenderer.text(List.of(Component.text("A\nB")),
        TextBlockLayout.builder().y(240).build()));
    var clipped = ScreenUiRenderer.layout(List.of(Component.text("ABCDEFG")),
        TextBlockLayout.builder().maxWidth(18).overflow(TextOverflow.ELLIPSIS).build());
    assertEquals("AB...", text(clipped.getFirst()));
  }

  @Test public void longWordsAreSplitWithoutDroppingCharacters() {
    var lines = ScreenUiRenderer.layout(List.of(Component.text("ABCDEFG")),
        TextBlockLayout.builder().maxWidth(18).build());
    assertEquals(List.of("ABC", "DEF", "G"), lines.stream().map(ScreenUiRendererTest::text).toList());
  }

  private static String text(ScreenUiRenderer.Line line) {
    return line.glyphs().stream().map(ScreenUiRenderer.Glyph::text).collect(java.util.stream.Collectors.joining());
  }
}

