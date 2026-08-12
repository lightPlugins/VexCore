package dev.vexsoft.core.common.service.requirement;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementRegistry;
import dev.vexsoft.core.common.service.execution.AbstractExecutionRegistry;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentKind;
import dev.vexsoft.core.requirement.Requirement;

/** Owner-scoped requirement extension registry. */
@Dependencies(ExecutionComponentCoordinatorService.class)
public final class VexRequirementRegistry extends AbstractExecutionRegistry
    implements RequirementRegistry {

  /** Creates the registry facade for the current owner. */
  public VexRequirementRegistry(final VexServiceRegistry services) {
    super(services, ExecutionComponentKind.REQUIREMENT);
  }

  @Override
  public void register(final String key, final Class<? extends Requirement> requirementType) {
    registerComponent(key, requirementType);
  }

  @Override
  public boolean unregister(final String key) {
    return unregisterComponent(key);
  }
}
