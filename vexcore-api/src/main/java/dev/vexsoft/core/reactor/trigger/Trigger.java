package dev.vexsoft.core.reactor.trigger;

import dev.vexsoft.core.reactor.context.ReactorContext;

/** Describes a registered source of reaction invocations. */
public interface Trigger<C extends ReactorContext> {

  /** Returns the concrete context type produced by this trigger. */
  Class<C> getContextType();
}
