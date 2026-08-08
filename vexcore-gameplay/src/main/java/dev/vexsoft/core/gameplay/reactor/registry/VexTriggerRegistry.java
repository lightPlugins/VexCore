package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.trigger.Trigger;
import dev.vexsoft.core.gameplay.reactor.trigger.TriggerRegistry;

/** Default owner-scoped trigger registry. */
@Dependencies(ReactorRegistryCoordinatorService.class)
public final class VexTriggerRegistry extends AbstractReactorComponentRegistry
    implements TriggerRegistry {

  /** Creates a trigger registry for the current service owner. */
  public VexTriggerRegistry(final VexServiceRegistry services) {
    super(services, ReactorComponentKind.TRIGGER);
  }

  @Override
  public void register(final Class<? extends Trigger<?>> triggerType) {
    registerComponent(triggerType);
  }
}
