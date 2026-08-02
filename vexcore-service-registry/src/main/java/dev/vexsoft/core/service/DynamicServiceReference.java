package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;

final class DynamicServiceReference<T extends VexService> implements ServiceReference<T> {
  private final ServiceRegistry registry;
  private final Class<T> type;

  DynamicServiceReference(ServiceRegistry registry, Class<T> type) {
    this.registry = registry;
    this.type = type;
  }

  @Override
  public Optional<T> find() {
    // Resolve every time so reloads cannot leave callers with a stale service instance
    return registry.find(type);
  }
  @Override public T require() { return registry.require(type); }
  @Override public boolean isAvailable() { return registry.isAvailable(type); }
}
