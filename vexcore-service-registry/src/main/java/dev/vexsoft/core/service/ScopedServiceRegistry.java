package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceNotFoundException;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Value;

final class ScopedServiceRegistry implements VexServiceRegistry {

  private final DefaultServiceRegistry registry;
  @Getter
  private final ServiceOwner owner;
  private final Map<Class<? extends VexService>, ServiceDefinition<?>> queued =
      new LinkedHashMap<>();

  ScopedServiceRegistry(final DefaultServiceRegistry registry, final ServiceOwner owner) {
    this.registry = registry;
    this.owner = owner;
  }

  @Override
  public synchronized <T extends VexService> void register(
      final Class<T> serviceType,
      final Class<? extends T> implementationType
  ) {
    Objects.requireNonNull(serviceType, "serviceType");
    Objects.requireNonNull(implementationType, "implementationType");
    validateDefinition(serviceType, implementationType);
    if (queued.putIfAbsent(
        serviceType,
        new ServiceDefinition<>(serviceType, implementationType)
    ) != null) {
      throw new IllegalStateException("Service is already queued: " + serviceType.getName());
    }
  }

  @Override
  public synchronized void registerQueuedServices() {
    List<ServiceDefinition<?>> pending = new ArrayList<>(queued.values());
    List<Class<? extends VexService>> registered = new ArrayList<>();
    try {
      while (!pending.isEmpty()) {
        boolean progressed = false;
        for (ServiceDefinition<?> definition : new ArrayList<>(pending)) {
          if (dependenciesAvailable(definition.getImplementationType(), pending)) {
            registerDefinition(definition);
            registered.add(definition.getServiceType());
            pending.remove(definition);
            progressed = true;
          }
        }
        if (!progressed) {
          throw dependencyFailure(pending);
        }
      }
      queued.clear();
    } catch (RuntimeException | Error throwable) {
      // Do not leave a half-created service group behind after a constructor failure
      for (Class<? extends VexService> serviceType : registered.reversed()) {
        registry.unregister(owner, serviceType);
      }
      throw throwable;
    }
  }

  @Override
  public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
    return registry.find(owner, serviceType);
  }

  @Override
  public <T extends VexService> T require(final Class<T> serviceType) {
    return find(serviceType).orElseThrow(() -> new ServiceNotFoundException(serviceType));
  }

  @Override
  public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
    Objects.requireNonNull(serviceType, "serviceType");
    return new ScopedServiceReference<>(this, serviceType);
  }

  @Override
  public boolean isAvailable(final Class<? extends VexService> serviceType) {
    return find(serviceType).isPresent();
  }

  @Override
  public void unregister(final Class<? extends VexService> serviceType) {
    registry.unregister(owner, serviceType);
  }

  @Override
  public synchronized void unregisterOwnedServices() {
    queued.clear();
    registry.unregisterOwnedBy(owner);
  }

  private <T extends VexService> void validateDefinition(
      final Class<T> serviceType,
      final Class<? extends T> implementationType
  ) {
    if (!serviceType.isInterface()) {
      throw new IllegalArgumentException("Service type must be an interface: " + serviceType.getName());
    }
    if (implementationType.isInterface() || Modifier.isAbstract(implementationType.getModifiers())) {
      throw new IllegalArgumentException(
          "Service implementation must be concrete: " + implementationType.getName()
      );
    }
    if (implementationType.getAnnotation(Dependencies.class) == null) {
      throw new IllegalArgumentException(
          "Service implementation is missing @Dependencies: " + implementationType.getName()
      );
    }
    constructor(implementationType);
  }

  private boolean dependenciesAvailable(
      final Class<? extends VexService> implementationType,
      final List<ServiceDefinition<?>> pending
  ) {
    Dependencies dependencies = implementationType.getAnnotation(Dependencies.class);
    for (Class<? extends VexService> dependency : dependencies.value()) {
      if (isAvailable(dependency)) {
        continue;
      }
      boolean queuedDependency = pending.stream()
          .anyMatch(definition -> definition.getServiceType() == dependency);
      if (queuedDependency) {
        return false;
      }
      throw new ServiceNotFoundException(dependency);
    }
    return true;
  }

  private IllegalStateException dependencyFailure(final List<ServiceDefinition<?>> pending) {
    String implementations = pending.stream()
        .map(definition -> definition.getImplementationType().getName())
        .sorted()
        .reduce((left, right) -> left + ", " + right)
        .orElse("unknown");
    return new IllegalStateException("Circular service dependencies detected: " + implementations);
  }

  private <T extends VexService> void registerDefinition(final ServiceDefinition<T> definition) {
    try {
      T implementation = constructor(definition.getImplementationType()).newInstance(this);
      registry.register(owner, definition.getServiceType(), implementation);
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IllegalStateException(
          "Unable to create service " + definition.getImplementationType().getName(),
          exception
      );
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(
          "Service constructor failed: " + definition.getImplementationType().getName(),
          cause
      );
    }
  }

  private <T extends VexService> Constructor<? extends T> constructor(
      final Class<? extends T> implementationType
  ) {
    try {
      return implementationType.getConstructor(VexServiceRegistry.class);
    } catch (NoSuchMethodException exception) {
      throw new IllegalArgumentException(
          "Service implementation requires a public VexServiceRegistry constructor: "
              + implementationType.getName(),
          exception
      );
    }
  }

  @Value
  private static class ServiceDefinition<T extends VexService> {
    Class<T> serviceType;
    Class<? extends T> implementationType;
  }
}
