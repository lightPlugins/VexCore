package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class StaticInventoryElement extends AbstractInventoryElement {

  private final ItemStack item;

  public StaticInventoryElement(final ItemStack item) {
    this(item, null);
  }

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
