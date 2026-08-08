package dev.vexsoft.core.gameplay.reactor.effect;

import dev.vexsoft.core.api.service.VexService;

/** Registers effect classes owned by the current service scope. */
public interface EffectRegistry extends VexService {

  /** Registers and creates one annotated effect class. */
  void register(Class<? extends Effect<?>> effectType);
}
