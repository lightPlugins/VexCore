package dev.vexsoft.core.inventory.page;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.List;
import java.util.Objects;

public final class CollectionPageSource<T> implements PageSource<T> {

  private final List<T> items;

  public CollectionPageSource(final List<T> items) {
    this.items = List.copyOf(Objects.requireNonNull(items, "items"));
  }

  @Override
  public List<T> getItems(final InventoryContext context) {
    return items;
  }
}
