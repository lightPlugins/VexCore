package dev.vexsoft.core.paper.inventory;

import dev.vexsoft.core.inventory.InventoryElement;
import dev.vexsoft.core.inventory.InventoryKey;
import dev.vexsoft.core.inventory.InventoryView;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;

@Getter
final class VexInventorySession {

  private final UUID viewerId;
  private final VexInventoryHolder holder;
  private final Deque<InventoryHistoryEntry> history = new ArrayDeque<>();
  @Setter
  private InventoryView currentView;
  @Setter
  private Inventory inventory;
  @Setter
  private Map<Integer, InventoryElement> renderedElements = Collections.emptyMap();
  @Setter
  private boolean suppressNextClose;

  VexInventorySession(final UUID viewerId, final VexInventoryHolder holder) {
    this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
    this.holder = Objects.requireNonNull(holder, "holder");
  }

  void push(final InventoryView view) {
    history.push(new InventoryHistoryEntry(view));
  }

  InventoryView pop(final int steps) {
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
    if (history.size() < steps) {
      throw new IllegalStateException(
          "Cannot navigate " + steps + " steps through an inventory history of " + history.size()
      );
    }
    InventoryHistoryEntry target = null;
    for (int index = 0; index < steps; index++) {
      target = history.pop();
    }
    return Objects.requireNonNull(target).getView();
  }

  Optional<InventoryView> popTo(final InventoryKey key) {
    Objects.requireNonNull(key, "key");
    boolean available = history.stream().anyMatch(entry -> entry.getKey().equals(key));
    if (!available) {
      return Optional.empty();
    }
    InventoryHistoryEntry entry;
    do {
      entry = history.pop();
    } while (!entry.getKey().equals(key));
    return Optional.of(entry.getView());
  }

  void updateRenderedElements(final Map<Integer, InventoryElement> elements) {
    renderedElements = Map.copyOf(Objects.requireNonNull(elements, "elements"));
  }
}
