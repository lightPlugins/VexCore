package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;

/**
 * Defines a VexCore module with a managed enable and disable lifecycle
 */
public interface VexModule extends ServiceOwner {
  /** Loads the module and registers the services it provides */
  void enable(VexServiceRegistry services);

  /** Activates runtime hooks after the owning plugin has been enabled */
  default void start() { }

  /** Stops the module and releases resources owned by it */
  default void disable() { }
}
