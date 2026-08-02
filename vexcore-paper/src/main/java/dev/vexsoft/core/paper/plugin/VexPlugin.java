package dev.vexsoft.core.paper.plugin;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexScheduleService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

public abstract class VexPlugin extends JavaPlugin implements ConfigurationOwner {

  private VexServiceRegistry services;
  private VexLogger logger;

  @Override
  public final void onLoad() {
    // Bukkit's registry is the bootstrap bridge before our own scoped registry exists
    ServiceRegistry registry = Bukkit.getServicesManager().load(ServiceRegistry.class);
    if (registry == null) {
      throw new IllegalStateException(
          "VexCore is not loaded. Add VexCore as a required plugin dependency."
      );
    }
    services = registry.scoped(this);
    try {
      services.register(ConfigurationService.class, VexConfigurationService.class);
      services.register(ScheduleService.class, VexScheduleService.class);
      configureServices(services);
      services.registerQueuedServices();
      onVexLoad();
    } catch (RuntimeException | Error throwable) {
      cleanupInfrastructure();
      throw throwable;
    }
  }

  @Override
  public final void onEnable() {
    onVexEnable();
  }

  @Override
  public final void onDisable() {
    try {
      onVexDisable();
    } finally {
      cleanupInfrastructure();
    }
  }

  /** Queues the services provided by this plugin */
  protected void configureServices(final VexServiceRegistry services) { }

  protected void onVexLoad() { }

  protected void onVexEnable() { }

  protected void onVexDisable() { }

  protected String consolePrefix() {
    return "<dark_gray>[<aqua>" + getName() + "</aqua>]</dark_gray> ";
  }

  public final VexServiceRegistry services() {
    if (services == null) {
      throw new IllegalStateException("VexPlugin has not been loaded yet");
    }
    return services;
  }

  @Override
  public final @NonNull VexLogger getLogger() {
    if (logger == null) {
      logger = new VexLogger(getName(), consolePrefix());
    }
    return logger;
  }

  @Override
  public final String serviceOwnerName() {
    return getName();
  }

  @Override
  public final Path configurationDirectory() {
    Path pluginsDirectory = getDataFolder().toPath().toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getName()).normalize();
  }

  @Override
  public final Optional<InputStream> configurationResource(final String resourcePath) {
    return Optional.ofNullable(getResource(resourcePath));
  }

  @Override
  public final void configurationWarning(final String message, final Throwable cause) {
    if (cause == null) {
      getLogger().warning(message);
    } else {
      getLogger().log(Level.WARNING, message, cause);
    }
  }

  private void cleanupInfrastructure() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }
}
