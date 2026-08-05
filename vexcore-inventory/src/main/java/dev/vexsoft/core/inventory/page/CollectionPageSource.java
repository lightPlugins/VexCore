package dev.vexsoft.core.inventory.page;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.List;
import java.util.Objects;

/** Immutable page source backed by a snapshot of a list. */
public final class CollectionPageSource<T> implements PageSource<T> {

  private final List<T> items;

  /** Copies the supplied items into an immutable page source. */
  public CollectionPageSource(final List<T> items) {
    this.items = List.copyOf(Objects.requireNonNull(items, "items"));
  }

  @Override
  public List<T> getItems(final InventoryContext context) {
    return items;
  }
}
