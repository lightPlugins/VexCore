package dev.vexsoft.core.inventory.element;

import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryElement;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Base element that optionally delegates clicks to a supplied handler. */
public abstract class AbstractInventoryElement implements InventoryElement {

  private final BiConsumer<InventoryContext, InventoryClickEvent> clickHandler;

  protected AbstractInventoryElement() {
    this(null);
  }

  protected AbstractInventoryElement(
      final BiConsumer<InventoryContext, InventoryClickEvent> clickHandler
  ) {
    this.clickHandler = clickHandler;
  }

  @Override
  public void onClick(final InventoryContext context, final InventoryClickEvent event) {
    if (clickHandler != null) {
      clickHandler.accept(
          Objects.requireNonNull(context, "context"),
          Objects.requireNonNull(event, "event")
      );
    }
  }

  @Override
  public abstract ItemStack render(InventoryContext context);
}
