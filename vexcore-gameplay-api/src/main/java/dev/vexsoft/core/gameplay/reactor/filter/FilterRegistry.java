package dev.vexsoft.core.gameplay.reactor.filter;

import dev.vexsoft.core.api.service.VexService;

/** Registers filter classes owned by the current service scope. */
public interface FilterRegistry extends VexService {

  /** Registers and creates one annotated filter class. */
  void register(Class<? extends Filter<?>> filterType);
}
