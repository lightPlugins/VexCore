package dev.vexsoft.core.common.service.registry;

import dev.vexsoft.core.api.service.registry.DuplicateServiceException;
import dev.vexsoft.core.api.service.registry.ServiceNotFoundException;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.ServiceRegistry;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;

/** Stores owner-bound services, resolves scoped lookups, and closes services when they are removed. */
public final class DefaultServiceRegistry implements ServiceRegistry {

    private final ConcurrentHashMap<Class<? extends VexService>, List<Registration<?>>> registrations =
        new ConcurrentHashMap<>();

    @Override
    public VexServiceRegistry scoped(final ServiceOwner owner) {
        return new ScopedServiceRegistry(this, Objects.requireNonNull(owner, "owner"), List.of());
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
        return find(List.of(), serviceType);
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

        List<VexService> removedServices = new ArrayList<>();

        registrations.forEach((serviceType, serviceRegistrations) -> {
            synchronized (serviceRegistrations) {
                serviceRegistrations.removeIf(registration -> {
                    if (registration.getOwner() == owner) {
                        removedServices.add(registration.getImplementation());

                        return true;
                    }

                    return false;
                });

                if (serviceRegistrations.isEmpty()) {
                    registrations.remove(serviceType, serviceRegistrations);
                }
            }
        });

        // Service cleanup may call back into the registry, so release registration locks first.
        closeServices(removedServices);
    }

    <T extends VexService> Optional<T> find(final List<ServiceOwner> preferredOwners, final Class<T> serviceType) {
        Objects.requireNonNull(preferredOwners, "preferredOwners");
        Objects.requireNonNull(serviceType, "serviceType");

        List<Registration<?>> serviceRegistrations = registrations.get(serviceType);

        if (serviceRegistrations == null) {
            return Optional.empty();
        }

        synchronized (serviceRegistrations) {
            for (ServiceOwner preferredOwner : preferredOwners) {
                for (Registration<?> registration : serviceRegistrations) {
                    if (registration.getOwner() == preferredOwner) {
                        return Optional.of(serviceType.cast(registration.getImplementation()));
                    }
                }
            }

            if (serviceRegistrations.size() == 1) {
                return Optional.of(serviceType.cast(serviceRegistrations.getFirst().getImplementation()));
            }

            if (serviceRegistrations.isEmpty()) {
                return Optional.empty();
            }
        }

        throw new IllegalStateException("Multiple implementations are registered for " + serviceType.getName());
    }

    <T extends VexService> void register(final ServiceOwner owner, final Class<T> serviceType, final T implementation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(implementation, "implementation");

        if (!serviceType.isInstance(implementation)) {
            throw new IllegalArgumentException(
                implementation.getClass().getName() + " does not implement " + serviceType.getName());
        }

        List<Registration<?>> serviceRegistrations =
            registrations.computeIfAbsent(serviceType, ignored -> new ArrayList<>());

        synchronized (serviceRegistrations) {
            for (Registration<?> existingRegistration : serviceRegistrations) {
                if (existingRegistration.getOwner() == owner) {
                    throw new DuplicateServiceException(serviceType, owner);
                }
            }

            serviceRegistrations.add(new Registration<>(owner, implementation));
        }
    }

    void unregister(final ServiceOwner owner, final Class<? extends VexService> serviceType) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(serviceType, "serviceType");

        List<Registration<?>> serviceRegistrations = registrations.get(serviceType);

        if (serviceRegistrations == null) {
            return;
        }

        List<VexService> removedServices = new ArrayList<>();

        synchronized (serviceRegistrations) {
            serviceRegistrations.removeIf(registration -> {
                if (registration.getOwner() == owner) {
                    removedServices.add(registration.getImplementation());

                    return true;
                }

                return false;
            });

            if (serviceRegistrations.isEmpty()) {
                registrations.remove(serviceType, serviceRegistrations);
            }
        }

        closeServices(removedServices);
    }

    private void closeServices(final List<VexService> services) {
        RuntimeException cleanupFailure = null;

        for (VexService service : services.reversed()) {
            if (!(service instanceof AutoCloseable closeableService)) {
                continue;
            }

            try {
                closeableService.close();
            } catch (Exception exception) {
                // Keep closing the remaining services even if one fails.
                if (cleanupFailure == null) {
                    cleanupFailure = new IllegalStateException("Failed to close a service", exception);
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @Value
    private static class Registration<T extends VexService> {

        ServiceOwner owner;
        T implementation;
    }
}
