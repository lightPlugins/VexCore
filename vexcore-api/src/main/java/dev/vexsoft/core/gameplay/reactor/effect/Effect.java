package dev.vexsoft.core.gameplay.reactor.effect;

import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import java.util.Map;

/** Compiles a configured side effect into its runtime representation. */
public interface Effect<C extends ReactorContext> {

  /** Returns the minimum context type required by this effect. */
  Class<C> getContextType();

  /** Compiles effect arguments during reload. */
  CompiledEffect<C> compile(Map<String, Object> arguments);
}
