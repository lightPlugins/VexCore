package dev.vexsoft.core.reactor.condition;

import dev.vexsoft.core.reactor.context.ReactorContext;
import java.util.Map;

/** Compiles dynamic reaction requirements such as permissions or stat comparisons. */
public interface Condition<C extends ReactorContext> {

  /** Returns the minimum context type required by this condition. */
  Class<C> getContextType();

  /** Compiles condition arguments during reload. */
  CompiledCondition<C> compile(Map<String, Object> arguments);
}
