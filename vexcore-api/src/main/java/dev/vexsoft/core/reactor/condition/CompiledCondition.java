package dev.vexsoft.core.reactor.condition;

import dev.vexsoft.core.reactor.context.ReactorContext;

/** Evaluates one precompiled dynamic requirement. */
@FunctionalInterface
public interface CompiledCondition<C extends ReactorContext> {

  /** Returns whether execution may continue for the supplied context. */
  boolean test(C context);
}
