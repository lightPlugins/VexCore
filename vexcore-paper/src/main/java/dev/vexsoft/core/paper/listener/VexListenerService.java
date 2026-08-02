package dev.vexsoft.core.paper.listener;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Dependencies
public final class VexListenerService implements ListenerService, AutoCloseable {

  private final VexServiceRegistry services;
  private final Plugin owner;
  private final List<Listener> listeners = new ArrayList<>();
  private boolean closed;

  public VexListenerService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    if (!(services.getOwner() instanceof Plugin plugin)) {
      throw new IllegalArgumentException("ListenerService owner must be a Bukkit plugin");
    }
    this.owner = plugin;
  }

  @Override
  public synchronized <T extends Listener> T register(final Class<T> listenerType) {
    Objects.requireNonNull(listenerType, "listenerType");
    if (closed) {
      throw new IllegalStateException("ListenerService is already closed");
    }
    T listener = instantiate(listenerType);
    Bukkit.getPluginManager().registerEvents(listener, owner);
    listeners.add(listener);
    return listener;
  }

  @Override
  public synchronized void unregisterAll() {
    for (Listener listener : listeners.reversed()) {
      HandlerList.unregisterAll(listener);
    }
    listeners.clear();
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      unregisterAll();
    }
  }

  private <T extends Listener> T instantiate(final Class<T> listenerType) {
    return VexClassFactory.create(listenerType, services, "Listener");
  }
}
