package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.paper.screenui.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UiToastStackTest {
  @Test void explicitScaleReachesToastTextAndBackground() {
    RecordingScreen screen = new RecordingScreen();
    var stack = new UiToastStack<Long>(screen, ScreenAnchor.BOTTOM_RIGHT, -8, -8, 4, 5, 0.7,
        Long::sum, n -> Component.text("+" + n), ignored -> NamedTextColor.AQUA);
    stack.push("stone", 2L, 0);
    stack.tick(0);
    assertEquals(0.7, screen.text.get("toast-0.text").scale());
    assertEquals(0.7, screen.textureScale);
  }
  private UiToastStack<Long> stack(RecordingScreen screen) {
    return new UiToastStack<>(screen, ScreenAnchor.BOTTOM_RIGHT, -8, -8, 4, 5,
        Long::sum, n -> Component.text("+" + n), ignored -> NamedTextColor.AQUA);
  }

  @Test void boundedQueuePreservesOldestAtBottomAndDrains() {
    RecordingScreen screen = new RecordingScreen();
    var stack = stack(screen);
    for (int i = 0; i < 5; i++) assertTrue(stack.push("item" + i, 1L, 23998));
    stack.tick(23998);
    assertEquals(5, screen.text.size());
    assertTrue(screen.text.get("toast-0.text").y() > screen.text.get("toast-4.text").y());
    assertEquals(0, screen.text.get("toast-0.text").animation().startTick());
    for (int i = 5; i < 69; i++) assertTrue(stack.push("item" + i, 1L, 23999));
    assertFalse(stack.push("overflow", 1L, 23999));
    for (long tick = 23999; tick < 26000; tick++) { stack.tick(tick); assertTrue(screen.text.size() <= 5); }
    assertTrue(stack.isEmpty());
    assertTrue(screen.text.isEmpty());
  }

  @Test void sustainedAcquisitionCoalescesWithoutImmortalMessagesOrIdleWrites() {
    RecordingScreen screen = new RecordingScreen();
    var stack = stack(screen);
    for (int tick = 0; tick < 10000; tick++) {
      for (int drop = 0; drop < 10; drop++) assertTrue(stack.push("stone", 1L, tick));
      stack.tick(tick);
      assertTrue(screen.text.size() <= 5);
    }
    for (int tick = 10000; tick < 11000; tick++) stack.tick(tick);
    assertTrue(stack.isEmpty());
    int writes = screen.writes;
    for (int tick = 11000; tick < 12000; tick++) stack.tick(tick);
    assertEquals(writes, screen.writes);
    stack.close();
    assertTrue(screen.closed);
    assertFalse(stack.push("late", 1L, 12001));
    assertTrue(stack.isEmpty());
  }

  public static final class RecordingScreen implements ScreenUi {
    private final Map<String, TextBlockLayout> text = new HashMap<>();
    private boolean closed;
    private int writes;
    private double textureScale;
    @Override public void textBlock(String id, List<Component> lines, TextBlockLayout layout) { text.put(id, layout); writes++; }
    @Override public void textureBlock(String id, UiTexture texture, TextureLayout layout) { writes++; textureScale = layout.scale(); }
    @Override public void setLines(String id, List<Component> lines) { writes++; }
    @Override public void remove(String id) { text.remove(id); }
    @Override public boolean isClosed() { return closed; }
    @Override public void close() { closed = true; text.clear(); }
  }
}
