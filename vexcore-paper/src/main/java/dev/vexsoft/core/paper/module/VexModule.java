package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceRegistry;

/**
 * Defines a VexCore module with a managed enable and disable lifecycle
 */
public interface VexModule extends ServiceOwner {
  /** Loads the module and registers the services it provides */
  public void enable(ServiceRegistry services);

  /** Activates runtime hooks after the owning plugin has been enabled */
  public default void start() { }

  /** Stops the module and releases resources owned by it */
  public default void disable() { }
}
