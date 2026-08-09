package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.reactor.condition.Condition;

import dev.vexsoft.core.api.service.registry.VexService;

/** Registers condition classes owned by the current service scope. */
public interface ConditionRegistry extends VexService {

  /** Registers and creates one annotated condition class. */
  void register(Class<? extends Condition<?>> conditionType);
}
