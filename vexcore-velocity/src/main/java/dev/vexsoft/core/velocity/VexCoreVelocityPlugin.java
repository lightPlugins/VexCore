package dev.vexsoft.core.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.messaging.MessageCodecService;
import dev.vexsoft.core.messaging.MessageTransportService;
import dev.vexsoft.core.messaging.VexMessageCodecService;
import dev.vexsoft.core.messaging.VexMessagingService;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import dev.vexsoft.core.velocity.bootstrap.ProxyPluginBootstrapService;
import dev.vexsoft.core.velocity.bootstrap.VexProxyPluginBootstrapService;
import dev.vexsoft.core.velocity.messaging.VexVelocityMessageTransportService;
import dev.vexsoft.core.velocity.messaging.VexProxyPingMessageHandler;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.slf4j.Logger;

@Plugin(
    id = "vexcore",
    name = "VexCore",
    version = "1.0.0-SNAPSHOT",
    description = "Modular infrastructure for VexSoft Velocity plugins"
)
public final class VexCoreVelocityPlugin implements VexVelocityCore, ConfigurationOwner {

  @Getter
  private final ProxyServer proxyServer;
  @Getter
  private final PluginContainer pluginContainer;
  private final Path platformDataDirectory;
  @Getter
  private final Logger platformLogger;
  private final DefaultServiceRegistry services = new DefaultServiceRegistry();
  private VexServiceRegistry coreServices;

  @Inject
  public VexCoreVelocityPlugin(
      final ProxyServer proxyServer,
      final PluginContainer pluginContainer,
      @DataDirectory final Path dataDirectory,
      final Logger logger
  ) {
    this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
    this.pluginContainer = Objects.requireNonNull(pluginContainer, "pluginContainer");
    this.platformDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    this.platformLogger = Objects.requireNonNull(logger, "logger");
  }

  @Subscribe(priority = Short.MAX_VALUE, async = false)
  public void onProxyInitialize(final ProxyInitializeEvent event) {
    Objects.requireNonNull(event, "event");
    coreServices = services.scoped(this);
    try {
      coreServices.register(MessageCodecService.class, VexMessageCodecService.class);
      coreServices.register(
          MessageTransportService.class,
          VexVelocityMessageTransportService.class
      );
      coreServices.registerQueuedServices();
      coreServices.register(ConfigurationService.class, VexConfigurationService.class);
      coreServices.register(CacheService.class, VexCacheService.class);
      coreServices.register(MessagingService.class, VexMessagingService.class);
      coreServices.register(
          ProxyPluginBootstrapService.class,
          VexProxyPluginBootstrapService.class
      );
      coreServices.registerQueuedServices();
      coreServices.require(MessagingService.class).register(VexProxyPingMessageHandler.class);
      coreServices.require(MessageTransportService.class).start();
      platformLogger.info("[VexCore] Velocity messaging successfully enabled");
    } catch (RuntimeException | Error throwable) {
      coreServices.unregisterOwnedServices();
      throw throwable;
    }
  }

  @Subscribe(priority = Short.MIN_VALUE, async = false)
  public void onProxyShutdown(final ProxyShutdownEvent event) {
    Objects.requireNonNull(event, "event");
    if (coreServices != null) {
      coreServices.unregisterOwnedServices();
      coreServices = null;
    }
  }

  @Override
  public ServiceRegistry getServiceRegistry() {
    return services;
  }

  @Override
  public String getServiceOwnerName() {
    return "VexCore";
  }

  @Override
  public Path getConfigurationDirectory() {
    Path pluginsDirectory = platformDataDirectory.toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the Velocity plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getServiceOwnerName()).normalize();
  }

  @Override
  public Optional<InputStream> getConfigurationResource(final String resourcePath) {
    return Optional.ofNullable(getClass().getClassLoader().getResourceAsStream(resourcePath));
  }

  @Override
  public void reportConfigurationWarning(final String message, final Throwable cause) {
    if (cause == null) {
      platformLogger.warn("[VexCore] " + message);
    } else {
      platformLogger.warn("[VexCore] " + message, cause);
    }
  }
}
