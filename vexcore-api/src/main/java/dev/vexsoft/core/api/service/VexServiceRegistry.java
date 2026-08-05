package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Provides owner-scoped registration and hierarchical access to VexCore services.
 *
 * <p>Registrations made through this view belong to {@link #getOwner()}. Child scopes prefer their
 * own registrations and fall back through their parent hierarchy. Call
 * {@link #unregisterOwnedServices()} during owner shutdown to close and remove owned services.</p>
 */
public interface VexServiceRegistry {

  /** Returns the owner associated with this registry */
  ServiceOwner getOwner();

  /** Creates a child scope that falls back to this registry */
  VexServiceRegistry scoped(ServiceOwner owner);

  /**
   * Queues a concrete implementation for dependency-aware registration.
   *
   * <p>The implementation must be public, concrete, annotated with {@link Dependencies}, and expose
   * a public constructor accepting this registry. It is not visible until
   * {@link #registerQueuedServices()} succeeds.</p>
   */
  <T extends VexService> void register(
      Class<T> serviceType,
      Class<? extends T> implementationType
  );

  /**
   * Creates and publishes every queued service in dependency order.
   *
   * <p>If creation fails, services created by this batch are rolled back before the failure is
   * propagated.</p>
   */
  void registerQueuedServices();

  /** Finds the current implementation of the requested service type */
  <T extends VexService> Optional<T> find(Class<T> serviceType);

  /**
   * Resolves the requested service or fails when it is unavailable.
   *
   * @throws ServiceNotFoundException if no visible registration exists
   */
  <T extends VexService> T require(Class<T> serviceType);

  /** Creates a dynamic reference to the requested service type */
  <T extends VexService> ServiceReference<T> reference(Class<T> serviceType);

  /** Checks whether the requested service type is currently registered */
  boolean isAvailable(Class<? extends VexService> serviceType);

  /** Removes and closes a service when its registration belongs to this scope's owner. */
  void unregister(Class<? extends VexService> serviceType);

  /** Removes and closes every service registered through this owner scope. */
  void unregisterOwnedServices();
}
