package dev.vexsoft.core.velocity.plugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.velocity.VexVelocityCore;
import dev.vexsoft.core.velocity.bootstrap.ProxyPluginBootstrapService;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.slf4j.Logger;

/** Connects a Velocity plugin to VexCore's scoped infrastructure and lifecycle */
public abstract class VexProxyPlugin implements ConfigurationOwner {

  private final Logger platformLogger;
  @Getter
  private final ProxyServer proxyServer;
  @Getter
  private final PluginContainer pluginContainer;
  private final Path platformDataDirectory;
  private VexServiceRegistry services;
  private VexProxyLogger logger;
  private boolean initialized;

  protected VexProxyPlugin(
      final ProxyServer proxyServer,
      final PluginContainer pluginContainer,
      final Path dataDirectory,
      final Logger logger
  ) {
    this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
    this.pluginContainer = Objects.requireNonNull(pluginContainer, "pluginContainer");
    this.platformDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    this.platformLogger = Objects.requireNonNull(logger, "logger");
  }

  /** Initializes this plugin's scoped services when Velocity finishes loading plugins */
  @Subscribe(async = false)
  public final void onProxyInitialize(final ProxyInitializeEvent event) {
    Objects.requireNonNull(event, "event");
    try {
      ServiceRegistry registry = resolveCore().getServiceRegistry();
      services = registry.scoped(this);
      registry.require(ProxyPluginBootstrapService.class).initialize(services);
      registerServices();
      services.registerQueuedServices();
      registerMessages(services.require(MessagingService.class));
      onVexProxyEnable();
      initialized = true;
    } catch (RuntimeException | Error throwable) {
      cleanupInfrastructure();
      throw throwable;
    }
  }

  /** Shuts down this plugin and removes every service owned by its scope */
  @Subscribe(async = false)
  public final void onProxyShutdown(final ProxyShutdownEvent event) {
    Objects.requireNonNull(event, "event");
    try {
      if (initialized) {
        onVexProxyDisable();
      }
    } finally {
      initialized = false;
      cleanupInfrastructure();
    }
  }

  /** Queues services provided by this plugin before message handlers are registered */
  protected void registerServices() { }

  /** Registers message handler classes through this plugin's scoped messaging service */
  protected void registerMessages(final MessagingService messages) { }

  /** Runs plugin-specific work after scoped infrastructure has initialized */
  protected void onVexProxyEnable() { }

  /** Runs plugin-specific shutdown work before owned services are removed */
  protected void onVexProxyDisable() { }

  /** Returns the plain console prefix prepended to this plugin's log output */
  protected String getConsolePrefix() {
    return "[" + getServiceOwnerName() + "] ";
  }

  /** Returns the service registry scoped to this proxy plugin */
  public final VexServiceRegistry getServices() {
    if (services == null) {
      throw new IllegalStateException("VexProxyPlugin has not been initialized yet");
    }
    return services;
  }

  /** Returns the prefix-aware logger owned by this proxy plugin */
  public final VexProxyLogger getLogger() {
    if (logger == null) {
      logger = new VexProxyLogger(platformLogger, getConsolePrefix());
    }
    return logger;
  }

  @Override
  public final String getServiceOwnerName() {
    return pluginContainer.getDescription().getName()
        .orElse(pluginContainer.getDescription().getId());
  }

  @Override
  public final Path getConfigurationDirectory() {
    Path pluginsDirectory = platformDataDirectory.toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the Velocity plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getServiceOwnerName()).normalize();
  }

  @Override
  public final Optional<InputStream> getConfigurationResource(final String resourcePath) {
    return Optional.ofNullable(getClass().getClassLoader().getResourceAsStream(resourcePath));
  }

  @Override
  public final void reportConfigurationWarning(final String message, final Throwable cause) {
    if (cause == null) {
      getLogger().warning(message);
    } else {
      getLogger().warning(message, cause);
    }
  }

  private VexVelocityCore resolveCore() {
    PluginContainer coreContainer = proxyServer.getPluginManager()
        .getPlugin("vexcore")
        .orElseThrow(() -> new IllegalStateException(
            "VexCore is not installed on this Velocity proxy"
        ));
    return coreContainer.getInstance()
        .filter(VexVelocityCore.class::isInstance)
        .map(VexVelocityCore.class::cast)
        .orElseThrow(() -> new IllegalStateException("Unable to access the VexCore Velocity API"));
  }

  private void cleanupInfrastructure() {
    if (services != null) {
      services.unregisterOwnedServices();
      services = null;
    }
  }
}
