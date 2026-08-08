package dev.vexsoft.core.gameplay.reactor.condition;

import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;

/** Evaluates one precompiled dynamic requirement. */
@FunctionalInterface
public interface CompiledCondition<C extends ReactorContext> {

  /** Returns whether execution may continue for the supplied context. */
  boolean test(C context);
}
