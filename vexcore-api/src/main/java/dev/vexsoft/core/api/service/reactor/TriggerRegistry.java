package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.reactor.trigger.Trigger;

import dev.vexsoft.core.api.service.registry.VexService;

/** Registers trigger classes owned by the current service scope. */
public interface TriggerRegistry extends VexService {

  /** Registers and creates one annotated trigger class. */
  void register(Class<? extends Trigger<?>> triggerType);
}
