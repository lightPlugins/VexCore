package dev.vexsoft.core.paper.listener;

import dev.vexsoft.core.api.service.VexService;
import org.bukkit.event.Listener;

/**
 * Creates and registers Bukkit listener classes for the current plugin
 */
public interface ListenerService extends VexService {

  /** Creates and registers the given listener class for the owning plugin */
  public <T extends Listener> T register(Class<T> listenerType);

  /** Unregisters every listener owned by this service */
  public void unregisterAll();
}
