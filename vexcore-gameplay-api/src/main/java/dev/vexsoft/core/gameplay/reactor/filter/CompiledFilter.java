package dev.vexsoft.core.gameplay.reactor.filter;

import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;

/** Performs a precompiled, side-effect-free context match. */
@FunctionalInterface
public interface CompiledFilter<C extends ReactorContext> {

  /** Returns whether the supplied context matches. */
  boolean test(C context);
}
