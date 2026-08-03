package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryKey;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BackInventoryElement extends AbstractInventoryElement {

  private final ItemStack item;
  private final Consumer<InventoryContext> action;

  public BackInventoryElement() {
    this(createDefaultItem(), context -> context.getInventoryService().back(context.getViewer()));
  }

  public BackInventoryElement(final int steps) {
    this(
        createDefaultItem(),
        context -> context.getInventoryService().back(context.getViewer(), steps)
    );
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
  }

  public BackInventoryElement(final InventoryKey target) {
    this(createDefaultItem(), targetAction(target));
  }

  public BackInventoryElement(final ItemStack item) {
    this(item, context -> context.getInventoryService().back(context.getViewer()));
  }

  public BackInventoryElement(final ItemStack item, final int steps) {
    this(item, context -> context.getInventoryService().back(context.getViewer(), steps));
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least one");
    }
  }

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
      final org.bukkit.event.inventory.InventoryClickEvent event
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
