package dev.vexsoft.core.common.service.execution;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;

/** Shared owner-aware implementation for the public extension registries. */
public abstract class AbstractExecutionRegistry implements AutoCloseable {

  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final ExecutionComponentCoordinatorService coordinator;
  private final ExecutionComponentKind kind;

  /** Captures the owner and shared coordinator. */
  protected AbstractExecutionRegistry(
      final VexServiceRegistry services,
      final ExecutionComponentKind kind
  ) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(ExecutionComponentCoordinatorService.class);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  /** Registers one class for this owner. */
  protected final void registerComponent(final String key, final Class<?> type) {
    coordinator.register(owner, services, kind, key, type);
  }

  /** Removes one class for this owner. */
  protected final boolean unregisterComponent(final String key) {
    return coordinator.unregister(owner, kind, key);
  }

  @Override
  public final void close() {
    coordinator.unregisterOwner(owner, kind);
  }
}
