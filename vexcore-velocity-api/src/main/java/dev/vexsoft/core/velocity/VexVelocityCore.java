package dev.vexsoft.core.velocity;

import dev.vexsoft.core.api.service.registry.ServiceRegistry;

/** Exposes the Velocity root registry used to create owner-scoped plugin registries */
public interface VexVelocityCore {

  /** Returns the root service registry owned by VexCore on this proxy */
  ServiceRegistry getServiceRegistry();
}
