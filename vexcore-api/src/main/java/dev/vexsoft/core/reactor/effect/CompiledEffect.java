package dev.vexsoft.core.reactor.effect;

import dev.vexsoft.core.reactor.context.ReactorContext;

/** Executes one precompiled reaction side effect. */
@FunctionalInterface
public interface CompiledEffect<C extends ReactorContext> {

  /** Applies this effect to the supplied context. */
  void execute(C context);
}
