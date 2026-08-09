package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.effect.Effect;
import dev.vexsoft.core.api.service.reactor.EffectRegistry;

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
