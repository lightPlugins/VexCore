package dev.vexsoft.core.common.service.registry;

import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class ScopedServiceReference<T extends VexService> implements ServiceReference<T> {

  @NonNull
  private final VexServiceRegistry registry;
  @NonNull
  private final Class<T> serviceType;

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
