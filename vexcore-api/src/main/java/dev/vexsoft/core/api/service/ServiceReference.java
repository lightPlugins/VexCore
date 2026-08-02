package dev.vexsoft.core.api.service;

import java.util.Optional;

/**
 * Keeps a dynamic reference to a service that may be registered later
 */
public interface ServiceReference<T extends VexService> {

  /**
   * Resolves the currently registered service when one is available
   *
   * @return the current service, or an empty optional
   */
  public Optional<T> find();

  /**
   * Resolves the currently registered service or fails when it is unavailable
   *
   * @return the current service
   */
  public T require();

  /**
   * Checks whether the referenced service is currently registered
   *
   * @return {@code true} when the service is available
   */
  public boolean isAvailable();
}
