package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryElement;
import dev.vexsoft.core.inventory.InventoryKey;
import dev.vexsoft.core.inventory.InventoryView;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class VexInventorySessionTest {

  @Test
  void popsMultipleViewsWithoutOffByOneErrors() {
    VexInventorySession session = session();
    TestView menuA = new TestView("test:a");
    TestView menuB = new TestView("test:b");
    session.push(menuA);
    session.push(menuB);

    assertSame(menuA, session.pop(2));
    assertTrue(session.getHistory().isEmpty());
  }

  @Test
  void popsDirectlyToTheNearestMatchingKey() {
    VexInventorySession session = session();
    TestView menuA = new TestView("test:a");
    session.push(menuA);
    session.push(new TestView("test:b"));

    assertSame(menuA, session.popTo(menuA.getKey()).orElseThrow());
    assertTrue(session.getHistory().isEmpty());
  }

  private VexInventorySession session() {
    UUID viewerId = UUID.randomUUID();
    return new VexInventorySession(
        viewerId,
        new VexInventoryHolder(viewerId, InventoryKey.of("test:current"))
    );
  }

  private record TestView(InventoryKey key) implements InventoryView {
    private TestView(final String key) {
      this(InventoryKey.of(key));
    }

    @Override
    public InventoryKey getKey() {
      return key;
    }

    @Override
    public int getSize() {
      return 9;
    }

    @Override
    public Component getTitle(final InventoryContext context) {
      return Component.empty();
    }

    @Override
    public Map<Integer, InventoryElement> getElements(final InventoryContext context) {
      return Map.of();
    }
  }
}
