package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.ReactorContext;
import org.bukkit.inventory.ItemStack;

/** Exposes the item used during a Paper reaction invocation. */
public interface ItemReactorContext extends ReactorContext {

  /** Returns a snapshot of the involved item. */
  ItemStack getItem();
}
