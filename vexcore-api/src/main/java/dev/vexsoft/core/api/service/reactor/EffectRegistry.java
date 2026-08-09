package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.reactor.effect.Effect;

import dev.vexsoft.core.api.service.registry.VexService;

/** Registers effect classes owned by the current service scope. */
public interface EffectRegistry extends VexService {

  /** Registers and creates one annotated effect class. */
  void register(Class<? extends Effect<?>> effectType);
}
