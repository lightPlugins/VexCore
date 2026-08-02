package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.PluginServiceRegistry;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;

final class ScopedServiceRegistry implements PluginServiceRegistry {
  private final DefaultServiceRegistry registry;
  private final ServiceOwner owner;

  ScopedServiceRegistry(DefaultServiceRegistry registry, ServiceOwner owner) {
    this.registry = registry;
    this.owner = owner;
  }

  @Override public ServiceOwner owner() { return owner; }
  @Override public <T extends VexService> void register(Class<T> type, T service) { registry.register(owner, type, service); }
  @Override public <T extends VexService> Optional<T> find(Class<T> type) { return registry.find(type); }
  @Override public <T extends VexService> T require(Class<T> type) { return registry.require(type); }
  @Override public <T extends VexService> ServiceReference<T> reference(Class<T> type) { return registry.reference(type); }
  @Override public boolean isAvailable(Class<? extends VexService> type) { return registry.isAvailable(type); }
  @Override public void unregister(Class<? extends VexService> type) { registry.unregister(owner, type); }
  @Override public void unregisterOwnedServices() { registry.unregisterOwnedBy(owner); }
}
