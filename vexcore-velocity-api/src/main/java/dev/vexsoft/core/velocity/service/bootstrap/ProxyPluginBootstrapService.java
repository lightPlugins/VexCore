package dev.vexsoft.core.velocity.service.bootstrap;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;

/** Prepares the owner-scoped infrastructure used by every Vex proxy plugin */
public interface ProxyPluginBootstrapService extends VexService {

  /** Queues every infrastructure service required by a Vex proxy plugin */
  void initialize(VexServiceRegistry services);
}
