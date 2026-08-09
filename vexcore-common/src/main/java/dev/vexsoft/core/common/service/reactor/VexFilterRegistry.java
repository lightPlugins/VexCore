package dev.vexsoft.core.common.service.reactor;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.reactor.filter.Filter;
import dev.vexsoft.core.api.service.reactor.FilterRegistry;

/** Default owner-scoped filter registry. */
@Dependencies(ReactorRegistryCoordinatorService.class)
public final class VexFilterRegistry extends AbstractReactorComponentRegistry implements FilterRegistry {

  /** Creates a filter registry for the current service owner. */
  public VexFilterRegistry(final VexServiceRegistry services) {
    super(services, ReactorComponentKind.FILTER);
  }

  @Override
  public void register(final Class<? extends Filter<?>> filterType) {
    registerComponent(filterType);
  }
}
