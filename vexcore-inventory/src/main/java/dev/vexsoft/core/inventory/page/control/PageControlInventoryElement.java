package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryKey;
import dev.vexsoft.core.inventory.element.AbstractInventoryElement;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Renders and cycles one page filter or sort control for the current viewer. */
public final class PageControlInventoryElement extends AbstractInventoryElement {

  private final Material material;
  private final Component title;
  private final InventoryKey inventoryKey;
  private final String areaId;
  private final PageControl control;
  private final PageControlStateStore states;
  private final Consumer<InventoryContext> callback;

  /** Creates an element whose left and right clicks cycle forward and backward through modes. */
  public PageControlInventoryElement(
      final Material material,
      final Component title,
      final InventoryKey inventoryKey,
      final String areaId,
      final PageControl control,
      final PageControlStateStore states,
      final Consumer<InventoryContext> callback
  ) {
    this.material = Objects.requireNonNull(material, "material");
    this.title = Objects.requireNonNull(title, "title");
    this.inventoryKey = Objects.requireNonNull(inventoryKey, "inventoryKey");
    this.areaId = Objects.requireNonNull(areaId, "areaId");
    this.control = Objects.requireNonNull(control, "control");
    this.states = Objects.requireNonNull(states, "states");
    this.callback = Objects.requireNonNull(callback, "callback");
    control.validate();
  }

  @Override
  public ItemStack render(final InventoryContext context) {
    String mode = states.getActiveMode(
        context.getViewer().getUniqueId(),
        inventoryKey,
        areaId,
        control.getControlId()
    ).orElse(control.getDefaultModeId());
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text().append(title).append(Component.text(": "))
        .append(control.getLabel(mode)).build());
    item.setItemMeta(meta);
    return item;
  }

  @Override
  public void onClick(final InventoryContext context, final InventoryClickEvent event) {
    if (event.getClick() == ClickType.RIGHT) {
      states.cyclePrevious(
          context.getViewer().getUniqueId(),
          inventoryKey,
          areaId,
          control
      );
    } else {
      states.cycleNext(
          context.getViewer().getUniqueId(),
          inventoryKey,
          areaId,
          control
      );
    }
    callback.accept(context);
  }
}
