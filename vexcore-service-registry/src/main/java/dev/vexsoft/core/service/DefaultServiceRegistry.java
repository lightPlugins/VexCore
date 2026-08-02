package dev.vexsoft.core.service;

import dev.vexsoft.core.api.service.DuplicateServiceException;
import dev.vexsoft.core.api.service.ServiceNotFoundException;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultServiceRegistry implements ServiceRegistry {

  private final ConcurrentHashMap<Class<? extends VexService>, List<Registration<?>>> registrations =
      new ConcurrentHashMap<>();

  @Override
  public VexServiceRegistry scoped(final ServiceOwner owner) {
    return new ScopedServiceRegistry(this, Objects.requireNonNull(owner, "owner"));
  }

  @Override
  public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
    return find(null, serviceType);
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
    return find(serviceType).isPresent();
  }

  @Override
  public void unregisterOwnedBy(final ServiceOwner owner) {
    Objects.requireNonNull(owner, "owner");
    List<VexService> removed = new ArrayList<>();
    registrations.forEach((type, values) -> {
      synchronized (values) {
        values.removeIf(registration -> {
          if (registration.owner() == owner) {
            removed.add(registration.implementation());
            return true;
          }
          return false;
        });
        if (values.isEmpty()) {
          registrations.remove(type, values);
        }
      }
    });
    closeServices(removed);
  }

  <T extends VexService> Optional<T> find(
      final ServiceOwner owner,
      final Class<T> serviceType
  ) {
    Objects.requireNonNull(serviceType, "serviceType");
    List<Registration<?>> values = registrations.get(serviceType);
    if (values == null) {
      return Optional.empty();
    }
    synchronized (values) {
      if (owner != null) {
        for (Registration<?> registration : values) {
          if (registration.owner() == owner) {
            return Optional.of(serviceType.cast(registration.implementation()));
          }
        }
      }
      if (values.size() == 1) {
        return Optional.of(serviceType.cast(values.getFirst().implementation()));
      }
      if (values.isEmpty()) {
        return Optional.empty();
      }
    }
    throw new IllegalStateException(
        "Multiple implementations are registered for " + serviceType.getName()
    );
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

    List<Registration<?>> values = registrations.computeIfAbsent(
        serviceType,
        ignored -> new ArrayList<>()
    );
    synchronized (values) {
      for (Registration<?> existing : values) {
        if (existing.owner() == owner) {
          throw new DuplicateServiceException(serviceType, owner);
        }
      }
      values.add(new Registration<>(owner, implementation));
    }
  }

  void unregister(final ServiceOwner owner, final Class<? extends VexService> serviceType) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(serviceType, "serviceType");
    List<Registration<?>> values = registrations.get(serviceType);
    if (values == null) {
      return;
    }
    List<VexService> removed = new ArrayList<>();
    synchronized (values) {
      values.removeIf(registration -> {
        if (registration.owner() == owner) {
          removed.add(registration.implementation());
          return true;
        }
        return false;
      });
      if (values.isEmpty()) {
        registrations.remove(serviceType, values);
      }
    }
    closeServices(removed);
  }

  private void closeServices(final List<VexService> services) {
    RuntimeException failure = null;
    for (VexService service : services.reversed()) {
      if (service instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception exception) {
          if (failure == null) {
            failure = new IllegalStateException("Failed to close a service", exception);
          } else {
            failure.addSuppressed(exception);
          }
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private record Registration<T extends VexService>(ServiceOwner owner, T implementation) {
  }
}
