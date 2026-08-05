package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Resolves services shared between VexCore modules and plugins.
 *
 * <p>A scoped view records ownership for future registrations while retaining hierarchical access
 * to services visible from its parent. Resolution is dynamic: removing a registration immediately
 * affects later calls and existing {@link ServiceReference} instances.</p>
 */
public interface ServiceRegistry {

  /**
   * Creates an owner-bound view of this registry
   *
   * @param owner the owner of future registrations
   * @return an owner-bound registry view
   */
  VexServiceRegistry scoped(ServiceOwner owner);

  /**
   * Finds the currently visible implementation of a service type.
   *
   * @param serviceType the service interface
   * @return the current service, or an empty optional when no visible registration exists
   */
  <T extends VexService> Optional<T> find(Class<T> serviceType);

  /**
   * Resolves a service and fails when no visible implementation is registered.
   *
   * @param serviceType the service interface
   * @return the registered service
   * @throws ServiceNotFoundException if the service is unavailable
   */
  <T extends VexService> T require(Class<T> serviceType);

  /**
   * Creates a dynamic reference that resolves the current implementation on every access.
   *
   * @param serviceType the service interface
   * @return a dynamic service reference
   */
  <T extends VexService> ServiceReference<T> reference(Class<T> serviceType);

  /**
   * Checks whether an implementation is registered for a service type
   *
   * @param serviceType the service interface
   * @return {@code true} when the service is available
   */
  boolean isAvailable(Class<? extends VexService> serviceType);

  /**
   * Removes every service registered by the specified owner.
   *
   * <p>Owned services implementing {@link AutoCloseable} are closed during removal.</p>
   *
   * @param owner the owner whose services should be removed
   */
  void unregisterOwnedBy(ServiceOwner owner);
}
