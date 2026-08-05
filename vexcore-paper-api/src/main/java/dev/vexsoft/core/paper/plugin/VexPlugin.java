package dev.vexsoft.core.paper.plugin;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.command.CommandService;
import dev.vexsoft.core.inventory.InventoryService;
import dev.vexsoft.core.paper.bootstrap.PluginBootstrapService;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.localization.LocalizationResourceScanner;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

/**
 * Provides the lifecycle and scoped infrastructure shared by every Vex plugin
 */
public abstract class VexPlugin extends JavaPlugin implements ConfigurationOwner, LocalizationOwner {

  private VexServiceRegistry services;
  private PluginBootstrapService bootstrap;
  private VexLogger logger;
  private boolean initialized;

  @Override
  public final void onLoad() {
    // Bukkit's registry is the bootstrap bridge before our own scoped registry exists
    ServiceRegistry registry = Bukkit.getServicesManager().load(ServiceRegistry.class);
    if (registry == null) {
      throw new IllegalStateException(
          "VexCore is not loaded. Add VexCore as a required plugin dependency."
      );
    }
    bootstrap = registry.require(PluginBootstrapService.class);
    services = registry.scoped(this);
    try {
      bootstrap.initialize(services);
      registerServices();
      services.registerQueuedServices();
      registerData(services.require(DataService.class));
      registerCommands(services.require(CommandService.class));
      registerInventories(services.require(InventoryService.class));
      onVexLoad();
      initialized = true;
    } catch (RuntimeException | Error throwable) {
      cleanupInfrastructure();
      throw throwable;
    }
  }

  @Override
  public final void onEnable() {
    if (!initialized) {
      getLogger().severe("Plugin cannot be enabled because its loading phase failed");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    bootstrap.enable(services);
    registerListeners(services.require(ListenerService.class));
    onVexEnable();
  }

  @Override
  public final void onDisable() {
    try {
      if (initialized) {
        onVexDisable();
      }
    } finally {
      initialized = false;
      cleanupInfrastructure();
    }
  }

  /** Queues the services provided by this plugin */
  protected void registerServices() { }

  /** Registers the commands provided by this plugin */
  protected void registerCommands(final CommandService commands) { }

  /** Registers the player data containers provided by this plugin */
  protected void registerData(final DataService data) { }

  /** Registers the listeners provided by this plugin */
  protected void registerListeners(final ListenerService listeners) { }

  /** Registers the inventories provided by this plugin */
  protected void registerInventories(final InventoryService inventories) { }

  /** Runs plugin-specific work after the loading phase */
  protected void onVexLoad() { }

  /** Runs plugin-specific work after the enabling phase */
  protected void onVexEnable() { }

  /** Runs plugin-specific work before infrastructure cleanup */
  protected void onVexDisable() { }

  /** Returns the MiniMessage prefix used for console output */
  protected String getConsolePrefix() {
    return "<dark_gray>[<aqua>" + getName() + "</aqua>]</dark_gray> ";
  }

  /** Returns the service registry scoped to this plugin */
  public final VexServiceRegistry getServices() {
    if (services == null) {
      throw new IllegalStateException("VexPlugin has not been loaded yet");
    }
    return services;
  }

  @Override
  public final @NonNull VexLogger getLogger() {
    if (logger == null) {
      logger = new VexLogger(getName(), getConsolePrefix());
    }
    return logger;
  }

  @Override
  public final String getServiceOwnerName() {
    return getName();
  }

  @Override
  public final Path getConfigurationDirectory() {
    Path pluginsDirectory = getDataFolder().toPath().toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getName()).normalize();
  }

  @Override
  public final Path getLocalizationDirectory() {
    return getConfigurationDirectory().resolve("languages");
  }

  @Override
  public final Collection<String> getLocalizationResources() {
    return LocalizationResourceScanner.scan(getFile().toPath());
  }

  @Override
  public final Optional<InputStream> getLocalizationResource(final String resourcePath) {
    return Optional.ofNullable(getResource(resourcePath));
  }

  @Override
  public String getMessagePrefixKey() {
    return "general.prefix";
  }

  @Override
  public final void reportLocalizationWarning(final String message, final Throwable cause) {
    reportConfigurationWarning(message, cause);
  }

  @Override
  public final Optional<InputStream> getConfigurationResource(final String resourcePath) {
    return Optional.ofNullable(getResource(resourcePath));
  }

  @Override
  public final void reportConfigurationWarning(final String message, final Throwable cause) {
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
