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
 * Base class that connects a Vex plugin to VexCore's scoped infrastructure and lifecycle.
 *
 * <p>During Bukkit's load phase it creates the plugin scope, registers infrastructure and queued
 * plugin services, then invokes data, command, inventory, and load hooks in that order. During the
 * enable phase it activates infrastructure before registering listeners and invoking
 * {@link #onVexEnable()}. Disable always removes and closes services owned by this plugin, even when
 * {@link #onVexDisable()} fails.</p>
 *
 * <p>Subclasses must declare VexCore as a required plugin dependency and override only the protected
 * Vex lifecycle hooks; Bukkit lifecycle methods are final.</p>
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

  /** Queues services provided by this plugin before data and feature registration begins. */
  protected void registerServices() { }

  /** Registers command classes through this plugin's scoped command service. */
  protected void registerCommands(final CommandService commands) { }

  /** Registers persistent player-data definitions before commands and inventories are loaded. */
  protected void registerData(final DataService data) { }

  /** Registers listener classes after the plugin and its infrastructure have been enabled. */
  protected void registerListeners(final ListenerService listeners) { }

  /** Registers inventory definitions during the plugin load phase. */
  protected void registerInventories(final InventoryService inventories) { }

  /** Runs plugin-specific work after all load-phase registrations complete successfully. */
  protected void onVexLoad() { }

  /** Runs plugin-specific work after infrastructure and listeners are enabled. */
  protected void onVexEnable() { }

  /** Runs plugin-specific shutdown work before owned services are removed and closed. */
  protected void onVexDisable() { }

  /** Returns the MiniMessage prefix prepended to this plugin's console output. */
  protected String getConsolePrefix() {
    return "<dark_gray>[<aqua>" + getName() + "</aqua>]</dark_gray> ";
  }

  /**
   * Returns the service registry scoped to this plugin.
   *
   * @return owner-scoped service registry
   * @throws IllegalStateException before the plugin load phase has initialized its scope
   */
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
