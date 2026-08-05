package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Dynamically resolves a service that may be replaced, removed, or registered later.
 *
 * <p>The reference does not retain a resolved implementation. Each access observes the registry's
 * current visible registration.</p>
 *
 * @param <T> referenced service contract
 */
public interface ServiceReference<T extends VexService> {

  /**
   * Resolves the currently registered service when one is available
   *
   * @return the current service, or an empty optional
   */
  Optional<T> find();

  /**
   * Resolves the currently registered service or fails when it is unavailable
   *
   * @return the current service
   * @throws ServiceNotFoundException if the service is unavailable
   */
  T require();

  /**
   * Checks whether the referenced service is currently registered
   *
   * @return {@code true} when the service is available
   */
  boolean isAvailable();
}
