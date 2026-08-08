package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.condition.Condition;
import dev.vexsoft.core.gameplay.reactor.condition.ConditionRegistry;

/** Default owner-scoped condition registry. */
@Dependencies(ReactorRegistryCoordinatorService.class)
public final class VexConditionRegistry extends AbstractReactorComponentRegistry
    implements ConditionRegistry {

  /** Creates a condition registry for the current service owner. */
  public VexConditionRegistry(final VexServiceRegistry services) {
    super(services, ReactorComponentKind.CONDITION);
  }

  @Override
  public void register(final Class<? extends Condition<?>> conditionType) {
    registerComponent(conditionType);
  }
}
