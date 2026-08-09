package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.reactor.filter.Filter;

import dev.vexsoft.core.api.service.registry.VexService;

/** Registers filter classes owned by the current service scope. */
public interface FilterRegistry extends VexService {

  /** Registers and creates one annotated filter class. */
  void register(Class<? extends Filter<?>> filterType);
}
