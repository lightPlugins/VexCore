package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.paper.screenui.*;
import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

public final class ScreenUiDialogueTest {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  @Test public void sentencesStayOnSeparatePagesAndOversizedSentencesRemainReadable() {
    var prepared = ScreenUiDialogueLayout.prepare(Component.text("NPC"),
        List.of(Component.text("First sentence. ").append(Component.text("Second sentence!"))),
        DialoguePanelLayout.defaults(), new V26_2ScreenUiVersionDefinition());
    assertEquals(2, prepared.pages().size());
    assertEquals("First sentence.", prepared.pages().getFirst().plainText().strip());
    assertEquals("Second sentence!", prepared.pages().getLast().plainText());
    var longSentence = ScreenUiDialogueLayout.prepare(Component.text("NPC"),
        List.of(Component.text("monolith ".repeat(35) + "end.")),
        DialoguePanelLayout.defaults(), new V26_2ScreenUiVersionDefinition());
    assertTrue(longSentence.pages().size() > 1);
    assertTrue(longSentence.pages().stream().allMatch(page -> page.lines().size() <= 3));
    assertTrue(longSentence.pages().getLast().plainText().endsWith("end."));
  }

  @Test public void wrapsBeforeRevealAndPreservesPageBoundariesAndStyles() {
    var prepared = ScreenUiDialogueLayout.prepare(Component.text("Researcher"),
        List.of(Component.text("First\nSecond\nThird\nFourth").decorate(TextDecoration.BOLD), Component.text("Last")),
        DialoguePanelLayout.defaults(), new V26_2ScreenUiVersionDefinition());
    assertEquals(3, prepared.pages().size());
    assertEquals(List.of("First", "Second", "Third"), prepared.pages().getFirst().lines().stream().map(PLAIN::serialize).toList());
    assertEquals("Fourth", prepared.pages().get(1).plainText());
    assertEquals("Last", prepared.pages().get(2).plainText());
    var glyphLayout = TextBlockLayout.builder().build();
    assertTrue(ScreenUiRenderer.layout(List.of(prepared.speaker()), glyphLayout).getFirst().glyphs().stream()
        .allMatch(glyph -> glyph.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE));
    assertTrue(ScreenUiRenderer.layout(prepared.pages().getFirst().lines(), glyphLayout).stream()
        .flatMap(line -> line.glyphs().stream())
        .allMatch(glyph -> glyph.style().decoration(TextDecoration.BOLD) == TextDecoration.State.FALSE));

    Map<String, List<Component>> content = new HashMap<>();
    Map<String, TextBlockLayout> positions = new HashMap<>();
    boolean[] closed = {false};
    int[] updates = {0};
    ScreenUi screen = (ScreenUi) Proxy.newProxyInstance(ScreenUi.class.getClassLoader(), new Class<?>[]{ScreenUi.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "textBlock" -> positions.put((String) args[0], (TextBlockLayout) args[2]);
            case "setLines" -> { content.put((String) args[0], (List<Component>) args[1]); updates[0]++; }
            case "isClosed" -> { return closed[0]; }
            case "close" -> closed[0] = true;
            default -> { }
          }
          return null;
        });
    var panel = new VexDialoguePanel(screen, prepared);
    panel.setHint(Component.text("Shift: Continue"));
    assertEquals(-68, positions.get("hint").y());
    panel.show(0, 7);
    assertEquals("First", PLAIN.serialize(content.get("line-0").getFirst()));
    assertEquals("Se", PLAIN.serialize(content.get("line-1").getFirst()));
    assertEquals(-120, positions.get("line-0").y());
    assertEquals(-106, positions.get("line-1").y());
    assertEquals(-92, positions.get("line-2").y());
    int count = updates[0];
    panel.show(0, 7);
    assertEquals(count, updates[0]);
    panel.show(1, 100);
    assertEquals("Fourth", PLAIN.serialize(content.get("line-0").getFirst()));
    assertEquals("", PLAIN.serialize(content.get("line-1").getFirst()));
    panel.close();
    assertThrows(IllegalStateException.class, () -> panel.show(0, 0));
  }

  @Test public void validatesGeometryAndSupportsArbitraryOriginsBeforeOpening() {
    var layout = DialoguePanelLayout.defaults();
    assertEquals(3, layout.linesPerPage());
    assertEquals(-144, layout.panelY());
    assertTrue(layout.bodyY() + (layout.linesPerPage() - 1) * 14 + 12 <= layout.hintY() - 6);
    assertDoesNotThrow(() -> ScreenUiDialogueLayout.validate(
        new DialoguePanelLayout(ScreenAnchor.BOTTOM_CENTER, 0, -85, 10, 8, 4, 2),
        new V26_2ScreenUiVersionDefinition()));
    for (ScreenAnchor anchor : ScreenAnchor.values()) {
      for (int y = -256; y <= 256; y++) {
        var version = new V26_2ScreenUiVersionDefinition();
        assertNotNull(version.textFont(anchor, y));
        assertNotNull(version.textureFont(UiTexture.PANEL, anchor, y));
        assertEquals(y, version.horizontalTransport(y) / 4096);
      }
    }
  }
}
