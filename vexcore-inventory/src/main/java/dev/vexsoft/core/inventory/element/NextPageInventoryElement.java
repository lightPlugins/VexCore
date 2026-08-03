package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class NextPageInventoryElement extends AbstractInventoryElement {

  private final Predicate<InventoryContext> pageAction;
  private final Function<InventoryContext, ItemStack> itemProvider;

  public NextPageInventoryElement(final Predicate<InventoryContext> pageAction) {
    this(context -> createDefaultItem(), pageAction);
  }

  public NextPageInventoryElement(
      final ItemStack item,
      final Predicate<InventoryContext> pageAction
  ) {
    this(context -> Objects.requireNonNull(item, "item").clone(), pageAction);
  }

  public NextPageInventoryElement(
      final Function<InventoryContext, ItemStack> itemProvider,
      final Predicate<InventoryContext> pageAction
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
    if (pageAction.test(context)) {
      context.getInventoryService().refresh(context.getViewer());
    }
  }

  private static ItemStack createDefaultItem() {
    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("Next Page"));
    item.setItemMeta(meta);
    return item;
  }
}
