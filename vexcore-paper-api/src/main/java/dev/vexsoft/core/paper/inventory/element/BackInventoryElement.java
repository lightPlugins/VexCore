package dev.vexsoft.core.paper.inventory.element;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Inventory element that navigates backward through a viewer's inventory history. */
public class BackInventoryElement extends AbstractInventoryElement {

  private final ItemStack item;
  private final Consumer<InventoryContext> action;

  /** Creates a default arrow that returns to the previous inventory. */
  public BackInventoryElement() {
    this(createDefaultItem(), context -> context.getInventoryService().back(context.getViewer()));
  }

  /** Creates a default arrow that moves backward by the requested number of history entries. */
  public BackInventoryElement(final int steps) {
    this(
        createDefaultItem(),
        context -> context.getInventoryService().back(context.getViewer(), steps)
    );
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
  }

  /** Creates a default arrow that returns to the most recent occurrence of a target inventory. */
  public BackInventoryElement(final InventoryKey target) {
    this(createDefaultItem(), targetAction(target));
  }

  /** Creates a custom item that returns to the previous inventory. */
  public BackInventoryElement(final ItemStack item) {
    this(item, context -> context.getInventoryService().back(context.getViewer()));
  }

  /** Creates a custom item that moves backward by the requested number of history entries. */
  public BackInventoryElement(final ItemStack item, final int steps) {
    this(item, context -> context.getInventoryService().back(context.getViewer(), steps));
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
  }

  /** Creates a custom item that returns to the most recent occurrence of a target inventory. */
  public BackInventoryElement(final ItemStack item, final InventoryKey target) {
    this(item, targetAction(target));
  }

  protected BackInventoryElement(
      final ItemStack item,
      final Consumer<InventoryContext> action
  ) {
    this.item = Objects.requireNonNull(item, "item").clone();
    this.action = Objects.requireNonNull(action, "action");
  }

  @Override
  public ItemStack render(final InventoryContext context) {
    return item.clone();
  }

  @Override
  public void onClick(
      final InventoryContext context,
      final InventoryClickEvent event
  ) {
    action.accept(Objects.requireNonNull(context, "context"));
  }

  private static ItemStack createDefaultItem() {
    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("Back"));
    item.setItemMeta(meta);
    return item;
  }

  private static Consumer<InventoryContext> targetAction(final InventoryKey target) {
    InventoryKey checkedTarget = Objects.requireNonNull(target, "target");
    return context -> context.getInventoryService().backTo(context.getViewer(), checkedTarget);
  }
}
