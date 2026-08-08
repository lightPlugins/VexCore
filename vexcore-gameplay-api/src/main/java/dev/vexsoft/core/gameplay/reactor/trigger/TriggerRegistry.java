package dev.vexsoft.core.gameplay.reactor.trigger;

import dev.vexsoft.core.api.service.VexService;

/** Registers trigger classes owned by the current service scope. */
public interface TriggerRegistry extends VexService {

  /** Registers and creates one annotated trigger class. */
  void register(Class<? extends Trigger<?>> triggerType);
}
