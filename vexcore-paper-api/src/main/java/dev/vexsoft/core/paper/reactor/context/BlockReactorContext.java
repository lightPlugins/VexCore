package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.ReactorContext;
import org.bukkit.block.Block;

/** Exposes the block involved in a Paper reaction invocation. */
public interface BlockReactorContext extends ReactorContext {

  /** Returns the involved block. */
  Block getBlock();
}
