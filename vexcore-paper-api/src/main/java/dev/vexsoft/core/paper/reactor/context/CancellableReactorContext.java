package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.ReactorContext;

/** Allows an effect to inspect or change event cancellation. */
public interface CancellableReactorContext extends ReactorContext {

  /** Returns whether the underlying event is cancelled. */
  boolean isCancelled();

  /** Changes cancellation of the underlying event. */
  void setCancelled(boolean cancelled);
}
