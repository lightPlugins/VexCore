package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;

abstract class AbstractReactorComponentRegistry implements AutoCloseable {

  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final ReactorRegistryCoordinatorService coordinator;
  private final ReactorComponentKind kind;

  AbstractReactorComponentRegistry(
      final VexServiceRegistry services,
      final ReactorComponentKind kind
  ) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(ReactorRegistryCoordinatorService.class);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  final void registerComponent(final Class<?> componentType) {
    coordinator.register(owner, services, kind, componentType);
  }

  @Override
  public final void close() {
    coordinator.unregisterOwner(owner, kind);
  }
}
