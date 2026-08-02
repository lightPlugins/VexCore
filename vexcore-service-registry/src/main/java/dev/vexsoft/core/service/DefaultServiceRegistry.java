package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.DuplicateServiceException;
import dev.vexsoft.core.api.service.PluginServiceRegistry;
import dev.vexsoft.core.api.service.ServiceNotFoundException;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexService;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultServiceRegistry implements ServiceRegistry {

  private final ConcurrentHashMap<Class<? extends VexService>, Registration<?>> registrations =
      new ConcurrentHashMap<>();

  @Override
  public PluginServiceRegistry scoped(final ServiceOwner owner) {
    return new ScopedServiceRegistry(this, Objects.requireNonNull(owner, "owner"));
  }

  @Override
  public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
    Objects.requireNonNull(serviceType, "serviceType");
    final Registration<?> registration = registrations.get(serviceType);
    if (registration == null) {
      return Optional.empty();
    }
    return Optional.of(serviceType.cast(registration.implementation()));
  }

  @Override
  public <T extends VexService> T require(final Class<T> serviceType) {
    return find(serviceType).orElseThrow(() -> new ServiceNotFoundException(serviceType));
  }

  @Override
  public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
    Objects.requireNonNull(serviceType, "serviceType");
    return new DynamicServiceReference<>(this, serviceType);
  }

  @Override
  public boolean isAvailable(final Class<? extends VexService> serviceType) {
    return registrations.containsKey(Objects.requireNonNull(serviceType, "serviceType"));
  }

  @Override
  public void unregisterOwnedBy(final ServiceOwner owner) {
    Objects.requireNonNull(owner, "owner");
    // Owner identity matters here because two plugins may expose the same display name
    registrations.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
  }

  <T extends VexService> void register(
      final ServiceOwner owner,
      final Class<T> serviceType,
      final T implementation
  ) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(serviceType, "serviceType");
    Objects.requireNonNull(implementation, "implementation");
    if (!serviceType.isInstance(implementation)) {
      throw new IllegalArgumentException(
          implementation.getClass().getName() + " does not implement " + serviceType.getName()
      );
    }

    final Registration<T> registration = new Registration<>(owner, implementation);
    final Registration<?> existing = registrations.putIfAbsent(serviceType, registration);
    if (existing != null) {
      throw new DuplicateServiceException(serviceType, existing.owner());
    }
  }

  void unregister(final ServiceOwner owner, final Class<? extends VexService> serviceType) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(serviceType, "serviceType");
    registrations.computeIfPresent(serviceType, (ignored, registration) ->
        registration.owner() == owner ? null : registration
    );
  }

  private record Registration<T extends VexService>(ServiceOwner owner, T implementation) {
  }
}
