package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class RefreshableInventoryElement extends AbstractInventoryElement {

  private final Function<InventoryContext, ItemStack> itemProvider;

  public RefreshableInventoryElement(final Function<InventoryContext, ItemStack> itemProvider) {
    this(itemProvider, null);
  }

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
