package dev.vexsoft.core.paper.plugin;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.command.CommandService;
import dev.vexsoft.core.command.VexCommandService;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.localization.VexLocalizationService;
import dev.vexsoft.core.inventory.InventoryService;
import dev.vexsoft.core.dialog.DialogService;
import dev.vexsoft.core.paper.dialog.VexDialogService;
import dev.vexsoft.core.item.ItemService;
import dev.vexsoft.core.paper.item.VexItemService;
import dev.vexsoft.core.paper.message.SendMessageService;
import dev.vexsoft.core.paper.message.VexSendMessageService;
import dev.vexsoft.core.paper.localization.LocalizationResourceScanner;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.listener.VexListenerService;
import dev.vexsoft.core.paper.inventory.VexInventoryListener;
import dev.vexsoft.core.paper.inventory.VexInventoryService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexScheduleService;
import dev.vexsoft.core.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.packets.service.FakeItemMetaService;
import dev.vexsoft.core.packets.service.InteractableHologramService;
import dev.vexsoft.core.packets.service.ItemDisplayPacketService;
import dev.vexsoft.core.packets.service.LightningPacketService;
import dev.vexsoft.core.packets.service.MobGlowPacketService;
import dev.vexsoft.core.packets.service.MobHitPacketService;
import dev.vexsoft.core.packets.service.TextDisplayPacketService;
import dev.vexsoft.core.paper.packet.service.VexDisplayPassengerPacketService;
import dev.vexsoft.core.paper.packet.service.VexFakeItemMetaService;
import dev.vexsoft.core.paper.packet.service.VexInteractableHologramService;
import dev.vexsoft.core.paper.packet.service.VexItemDisplayPacketService;
import dev.vexsoft.core.paper.packet.service.VexLightningPacketService;
import dev.vexsoft.core.paper.packet.service.VexMobGlowPacketService;
import dev.vexsoft.core.paper.packet.service.VexMobHitPacketService;
import dev.vexsoft.core.paper.packet.service.VexTextDisplayPacketService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Collection;
import java.util.logging.Level;

public abstract class VexPlugin extends JavaPlugin implements ConfigurationOwner, LocalizationOwner {

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
      services.register(InventoryService.class, VexInventoryService.class);
      services.register(CommandService.class, VexCommandService.class);
      services.register(CacheService.class, VexCacheService.class);
      services.register(ListenerService.class, VexListenerService.class);
      services.register(DialogService.class, VexDialogService.class);
      services.register(ItemService.class, VexItemService.class);
      services.register(DataService.class, VexDataService.class);
      services.register(LocalizationService.class, VexLocalizationService.class);
      services.register(SendMessageService.class, VexSendMessageService.class);
      services.register(TextDisplayPacketService.class, VexTextDisplayPacketService.class);
      services.register(ItemDisplayPacketService.class, VexItemDisplayPacketService.class);
      services.register(DisplayPassengerPacketService.class, VexDisplayPassengerPacketService.class);
      services.register(InteractableHologramService.class, VexInteractableHologramService.class);
      services.register(MobHitPacketService.class, VexMobHitPacketService.class);
      services.register(MobGlowPacketService.class, VexMobGlowPacketService.class);
      services.register(LightningPacketService.class, VexLightningPacketService.class);
      services.register(FakeItemMetaService.class, VexFakeItemMetaService.class);
      registerServices();
      services.registerQueuedServices();
      registerData(services.require(DataService.class));
      registerCommands(services.require(CommandService.class));
      registerInventories(services.require(InventoryService.class));
      onVexLoad();
    } catch (RuntimeException | Error throwable) {
      cleanupInfrastructure();
      throw throwable;
    }
  }

  @Override
  public final void onEnable() {
    ListenerService listeners = services.require(ListenerService.class);
    listeners.register(VexInventoryListener.class);
    registerListeners(listeners);
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
  protected void registerServices() { }

  /** Registers the commands provided by this plugin */
  protected void registerCommands(final CommandService commands) { }

  /** Registers the player data containers provided by this plugin */
  protected void registerData(final DataService data) { }

  /** Registers the listeners provided by this plugin */
  protected void registerListeners(final ListenerService listeners) { }

  /** Registers the inventories provided by this plugin */
  protected void registerInventories(final InventoryService inventories) { }

  protected void onVexLoad() { }

  protected void onVexEnable() { }

  protected void onVexDisable() { }

  protected String getConsolePrefix() {
    return "<dark_gray>[<aqua>" + getName() + "</aqua>]</dark_gray> ";
  }

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
