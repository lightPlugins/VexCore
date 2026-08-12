package dev.vexsoft.core.common.service.cost;

import dev.vexsoft.core.api.service.cost.CostRegistry;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.execution.AbstractExecutionRegistry;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.cost.Cost;

/** Owner-scoped cost extension registry. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexCostRegistry extends AbstractExecutionRegistry implements CostRegistry {

  /** Creates the registry facade for the current owner. */
  public VexCostRegistry(final VexServiceRegistry services) {
    super(services, ExecutionComponentKind.COST);
  }

  @Override
  public void register(final String key, final Class<? extends Cost> costType) {
    registerComponent(key, costType);
  }

  @Override
  public boolean unregister(final String key) {
    return unregisterComponent(key);
  }
}
