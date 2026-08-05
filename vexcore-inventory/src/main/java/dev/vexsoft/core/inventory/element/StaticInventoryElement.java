package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Renders defensive copies of a fixed item stack. */
public class StaticInventoryElement extends AbstractInventoryElement {

  private final ItemStack item;

  /** Creates a static element without a click handler. */
  public StaticInventoryElement(final ItemStack item) {
    this(item, null);
  }

  /** Creates a static element with an optional context-aware click handler. */
  public StaticInventoryElement(
      final ItemStack item,
      final BiConsumer<InventoryContext, InventoryClickEvent> clickHandler
  ) {
    super(clickHandler);
    this.item = Objects.requireNonNull(item, "item").clone();
  }

  @Override
  public ItemStack render(final InventoryContext context) {
    return item.clone();
  }
}
