package dev.vexsoft.core.paper.service.listeners;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import org.bukkit.event.Listener;

/**
 * Creates and registers Bukkit listener classes for the current plugin
 */
public interface ListenerService extends VexService {

  /**
   * Creates the given listener using the supplied service scope and registers it for the owning
   * plugin.
   */
  <T extends Listener> T register(Class<T> listenerType, VexServiceRegistry services);

  /** Unregisters every listener owned by this service */
  void unregisterAll();
}
