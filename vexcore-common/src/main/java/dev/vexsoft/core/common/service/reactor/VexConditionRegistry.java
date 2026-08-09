package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.condition.Condition;
import dev.vexsoft.core.api.service.reactor.ConditionRegistry;

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
