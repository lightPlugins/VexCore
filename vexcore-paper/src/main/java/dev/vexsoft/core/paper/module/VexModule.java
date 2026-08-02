package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceRegistry;

public interface VexModule extends ServiceOwner {
  /** Starts the module and registers the services it provides */
  public void enable(ServiceRegistry services);

  /** Stops the module and releases resources owned by it */
  public default void disable() { }
}
