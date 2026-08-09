package dev.vexsoft.core.paper.inventory.element;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Renders a fresh item from the current inventory context whenever the view refreshes. */
public class RefreshableInventoryElement extends AbstractInventoryElement {

  private final Function<InventoryContext, ItemStack> itemProvider;

  /** Creates a refreshable element without a click handler. */
  public RefreshableInventoryElement(final Function<InventoryContext, ItemStack> itemProvider) {
    this(itemProvider, null);
  }

  /** Creates a refreshable element with an optional context-aware click handler. */
  public RefreshableInventoryElement(
      final Function<InventoryContext, ItemStack> itemProvider,
      final BiConsumer<InventoryContext, InventoryClickEvent> clickHandler
  ) {
    super(clickHandler);
    this.itemProvider = Objects.requireNonNull(itemProvider, "itemProvider");
  }

  @Override
  public ItemStack render(final InventoryContext context) {
    ItemStack item = itemProvider.apply(Objects.requireNonNull(context, "context"));
    return item == null ? null : item.clone();
  }
}
