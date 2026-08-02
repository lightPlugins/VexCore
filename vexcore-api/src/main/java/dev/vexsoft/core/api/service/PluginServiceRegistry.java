package dev.vexsoft.core.api.service;

import java.util.Optional;

public interface PluginServiceRegistry {
  /** Returns the owner associated with this registry view */
  public ServiceOwner owner();

  /** Registers a service implementation owned by this registry's owner */
  public <T extends VexService> void register(Class<T> serviceType, T implementation);

  /** Finds the current implementation of the requested service type */
  public <T extends VexService> Optional<T> find(Class<T> serviceType);

  /** Resolves the requested service or fails when it is unavailable */
  public <T extends VexService> T require(Class<T> serviceType);

  /** Creates a dynamic reference to the requested service type */
  public <T extends VexService> ServiceReference<T> reference(Class<T> serviceType);

  /** Checks whether the requested service type is currently registered */
  public boolean isAvailable(Class<? extends VexService> serviceType);

  /** Removes a service when it is owned by this registry's owner */
  public void unregister(Class<? extends VexService> serviceType);

  /** Removes every service registered through this owner-bound view */
  public void unregisterOwnedServices();
}
