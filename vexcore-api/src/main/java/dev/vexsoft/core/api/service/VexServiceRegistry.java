package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Provides owner-scoped registration and access to VexCore services
 */
public interface VexServiceRegistry {

  /** Returns the owner associated with this registry */
  public ServiceOwner getOwner();

  /** Queues a service implementation for dependency-aware registration */
  public <T extends VexService> void register(
      Class<T> serviceType,
      Class<? extends T> implementationType
  );

  /** Creates and publishes every queued service in dependency order */
  public void registerQueuedServices();

  /** Finds the current implementation of the requested service type */
  public <T extends VexService> Optional<T> find(Class<T> serviceType);

  /** Resolves the requested service or fails when it is unavailable */
  public <T extends VexService> T require(Class<T> serviceType);

  /** Creates a dynamic reference to the requested service type */
  public <T extends VexService> ServiceReference<T> reference(Class<T> serviceType);

  /** Checks whether the requested service type is currently registered */
  public boolean isAvailable(Class<? extends VexService> serviceType);

  /** Removes a service when it is owned by this registry */
  public void unregister(Class<? extends VexService> serviceType);

  /** Removes every service registered through this registry */
  public void unregisterOwnedServices();
}
