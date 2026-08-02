package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Stores the services shared between VexCore modules and plugins
 */
public interface ServiceRegistry {

  /**
   * Creates an owner-bound view of this registry
   *
   * @param owner the owner of future registrations
   * @return an owner-bound registry view
   */
  public VexServiceRegistry scoped(ServiceOwner owner);

  /**
   * Finds the currently registered implementation of a service type
   *
   * @param serviceType the service interface
   * @return the registered service, or an empty optional
   */
  public <T extends VexService> Optional<T> find(Class<T> serviceType);

  /**
   * Resolves a service and fails when no implementation is registered
   *
   * @param serviceType the service interface
   * @return the registered service
   */
  public <T extends VexService> T require(Class<T> serviceType);

  /**
   * Creates a dynamic reference to the current implementation of a service
   *
   * @param serviceType the service interface
   * @return a dynamic service reference
   */
  public <T extends VexService> ServiceReference<T> reference(Class<T> serviceType);

  /**
   * Checks whether an implementation is registered for a service type
   *
   * @param serviceType the service interface
   * @return {@code true} when the service is available
   */
  public boolean isAvailable(Class<? extends VexService> serviceType);

  /**
   * Removes every service registered by the specified owner
   *
   * @param owner the owner whose services should be removed
   */
  public void unregisterOwnedBy(ServiceOwner owner);
}
