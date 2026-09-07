package dev.vexsoft.core.paper.inventory.element;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

/** A rendered slot backed by an external item store, including empty-slot placeholders. */
public final class CursorInventoryElement extends RefreshableInventoryElement {
  private final Consumer<InventoryContext> transfer;

  /**
   * The callback validates the cursor and atomically exchanges it with its backing store. Rejection
   * must leave the cursor and store unchanged. The framework never transfers the rendered icon and
   * refreshes the menu after the callback returns.
   */
  public CursorInventoryElement(
      Function<InventoryContext, ItemStack> renderer, Consumer<InventoryContext> transfer) {
    super(renderer);
    this.transfer = Objects.requireNonNull(transfer, "transfer");
  }

  /** Invokes the backing-storage transfer callback with the current viewer context. */
  public void transfer(InventoryContext context) {
    transfer.accept(context);
  }
}
