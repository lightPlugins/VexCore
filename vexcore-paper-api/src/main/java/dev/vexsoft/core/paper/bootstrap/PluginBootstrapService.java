package dev.vexsoft.core.paper.bootstrap;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;

/**
 * Prepares the scoped infrastructure used by every Vex plugin
 */
public interface PluginBootstrapService extends VexService {

  /** Queues every infrastructure service required by a Vex plugin */
  public void initialize(VexServiceRegistry services);

  /** Starts infrastructure that requires an enabled Bukkit plugin */
  public void enable(VexServiceRegistry services);
}
