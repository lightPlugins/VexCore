package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.effect.Effect;
import dev.vexsoft.core.gameplay.reactor.effect.EffectRegistry;

/** Default owner-scoped effect registry. */
@Dependencies(ReactorRegistryCoordinatorService.class)
public final class VexEffectRegistry extends AbstractReactorComponentRegistry implements EffectRegistry {

  /** Creates an effect registry for the current service owner. */
  public VexEffectRegistry(final VexServiceRegistry services) {
    super(services, ReactorComponentKind.EFFECT);
  }

  @Override
  public void register(final Class<? extends Effect<?>> effectType) {
    registerComponent(effectType);
  }
}
