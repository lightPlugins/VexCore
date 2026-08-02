package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;
import java.util.Optional;

final class ScopedServiceReference<T extends VexService> implements ServiceReference<T> {

  private final VexServiceRegistry registry;
  private final Class<T> serviceType;

  ScopedServiceReference(final VexServiceRegistry registry, final Class<T> serviceType) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.serviceType = Objects.requireNonNull(serviceType, "serviceType");
  }

  @Override
  public Optional<T> find() {
    return registry.find(serviceType);
  }

  @Override
  public T require() {
    return registry.require(serviceType);
  }

  @Override
  public boolean isAvailable() {
    return registry.isAvailable(serviceType);
  }
}
