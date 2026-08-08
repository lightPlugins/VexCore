package dev.vexsoft.core.gameplay.reactor;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import dev.vexsoft.core.gameplay.reactor.registry.ReactorRegistryCoordinatorService;
import java.util.Collection;
import java.util.Objects;

/** Default owner-scoped facade over the shared reaction runtime. */
@Dependencies(ReactorRegistryCoordinatorService.class)
public final class VexReactorEngine implements ReactorEngine, AutoCloseable {

  private final ServiceOwner owner;
  private final ReactorRegistryCoordinatorService coordinator;

  /** Creates a reaction engine facade for the current service owner. */
  public VexReactorEngine(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    coordinator = checkedServices.require(ReactorRegistryCoordinatorService.class);
  }

  @Override
  public void reload(final Collection<ReactionDefinition> definitions) {
    coordinator.reload(owner, definitions);
  }

  @Override
  public void dispatch(final String triggerId, final ReactorContext context) {
    coordinator.dispatch(triggerId, context);
  }

  @Override
  public void clear() {
    coordinator.clear(owner);
  }

  @Override
  public void close() {
    clear();
  }
}
