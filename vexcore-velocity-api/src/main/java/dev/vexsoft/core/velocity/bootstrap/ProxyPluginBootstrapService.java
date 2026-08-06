package dev.vexsoft.core.velocity.bootstrap;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;

/** Prepares the owner-scoped infrastructure used by every Vex proxy plugin */
public interface ProxyPluginBootstrapService extends VexService {

  /** Queues every infrastructure service required by a Vex proxy plugin */
  void initialize(VexServiceRegistry services);
}
