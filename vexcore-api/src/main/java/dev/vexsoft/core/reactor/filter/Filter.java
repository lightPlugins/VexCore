package dev.vexsoft.core.reactor.filter;

import dev.vexsoft.core.reactor.context.ReactorContext;

/** Compiles one configuration value into an efficient runtime filter. */
public interface Filter<C extends ReactorContext> {

  /** Returns the minimum context type required by this filter. */
  Class<C> getContextType();

  /** Compiles the filter-specific configuration value during reload. */
  CompiledFilter<C> compile(Object configuration);
}
