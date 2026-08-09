package dev.vexsoft.core.paper.inventory.element;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Element that returns a paged view and refreshes it after a successful transition. */
public class PreviousPageInventoryElement extends AbstractInventoryElement {

  private final BooleanSupplier pageAction;
  private final Function<InventoryContext, ItemStack> itemProvider;

  /** Creates a default arrow using the supplied page transition. */
  public PreviousPageInventoryElement(final BooleanSupplier pageAction) {
    this(context -> createDefaultItem(), pageAction);
  }

  /** Creates a fixed custom item using the supplied page transition. */
  public PreviousPageInventoryElement(final ItemStack item, final BooleanSupplier pageAction) {
    this(context -> Objects.requireNonNull(item, "item").clone(), pageAction);
  }

  /** Creates a context-sensitive item using the supplied page transition. */
  public PreviousPageInventoryElement(
      final Function<InventoryContext, ItemStack> itemProvider,
      final BooleanSupplier pageAction
  ) {
    this.pageAction = Objects.requireNonNull(pageAction, "pageAction");
    this.itemProvider = Objects.requireNonNull(itemProvider, "itemProvider");
  }

  @Override
  public ItemStack render(final InventoryContext context) {
    ItemStack item = itemProvider.apply(context);
    return item == null ? null : item.clone();
  }

  @Override
  public void onClick(final InventoryContext context, final InventoryClickEvent event) {
    if (pageAction.getAsBoolean()) {
      context.getInventoryService().refresh(context.getViewer());
    }
  }

  private static ItemStack createDefaultItem() {
    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("Previous Page"));
    item.setItemMeta(meta);
    return item;
  }
}
