package dev.vexsoft.core.paper;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.theme.ThemeColorService;
import dev.vexsoft.core.api.signal.SignalService;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.messaging.MessagingService;
import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.player.PlayerContainerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.paper.module.ModuleManager;
import dev.vexsoft.core.paper.module.PlatformModule;
import dev.vexsoft.core.paper.module.PlayerModule;
import dev.vexsoft.core.paper.module.LocalizationModule;
import dev.vexsoft.core.paper.module.PacketModule;
import dev.vexsoft.core.paper.module.DialogModule;
import dev.vexsoft.core.paper.module.ItemModule;
import dev.vexsoft.core.paper.bootstrap.PluginBootstrapService;
import dev.vexsoft.core.paper.bootstrap.VexPluginBootstrapService;
import dev.vexsoft.core.paper.listener.ListenerService;
import dev.vexsoft.core.paper.listener.VexListenerService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexScheduleService;
import dev.vexsoft.core.dialog.DialogService;
import dev.vexsoft.core.paper.dialog.VexDialogService;
import dev.vexsoft.core.item.ItemService;
import dev.vexsoft.core.paper.item.VexItemService;
import dev.vexsoft.core.paper.listener.VexPlayerLifecycleListener;
import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.plugin.VexLogger;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import dev.vexsoft.core.paper.command.CommandService;
import dev.vexsoft.core.command.VexCommandService;
import dev.vexsoft.core.cache.CacheService;
import dev.vexsoft.core.cache.VexCacheService;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.data.VexPlayerContainerService;
import dev.vexsoft.core.localization.VexCorePlayerData;
import dev.vexsoft.core.localization.VexLocalizationService;
import dev.vexsoft.core.localization.VexThemeColorService;
import dev.vexsoft.core.signal.SignalRegistryService;
import dev.vexsoft.core.signal.VexSignalRegistryService;
import dev.vexsoft.core.signal.VexSignalService;
import dev.vexsoft.core.messaging.MessageCodecService;
import dev.vexsoft.core.messaging.MessageTransportService;
import dev.vexsoft.core.messaging.VexMessageCodecService;
import dev.vexsoft.core.messaging.VexMessagingService;
import dev.vexsoft.core.paper.command.VexCoreCommand;
import dev.vexsoft.core.paper.localization.LocalizationResourceScanner;
import dev.vexsoft.core.paper.message.SendMessageService;
import dev.vexsoft.core.paper.message.VexSendMessageService;
import dev.vexsoft.core.paper.messaging.VexPaperMessageTransportService;
import dev.vexsoft.core.paper.messaging.ProxyPingService;
import dev.vexsoft.core.paper.messaging.VexProxyPingResponseHandler;
import dev.vexsoft.core.paper.messaging.VexProxyPingService;
import dev.vexsoft.core.paper.player.PaperPlayerService;
import dev.vexsoft.core.paper.player.VexPaperPlayerService;
import dev.vexsoft.core.paper.performance.PerformanceBossBarService;
import dev.vexsoft.core.paper.performance.ServerPerformanceService;
import dev.vexsoft.core.paper.performance.VexPerformanceBossBarListener;
import dev.vexsoft.core.paper.performance.VexPerformanceBossBarService;
import dev.vexsoft.core.paper.performance.VexServerPerformanceService;
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
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public final class VexCorePlugin extends JavaPlugin implements ConfigurationOwner, LocalizationOwner {
  private DefaultServiceRegistry services;
  private VexServiceRegistry coreServices;
  private ModuleManager modules;
  private VexLogger logger;
  private ScheduledTask playerAutosaveTask;
  private boolean initialized;

  @Override
  public void onLoad() {
    services = new DefaultServiceRegistry();
    getServer().getServicesManager().register(ServiceRegistry.class, services, this, ServicePriority.Normal);
    coreServices = services.scoped(this);
    modules = new ModuleManager(coreServices);
    modules.enable(new PlatformModule());
    coreServices.register(ScheduleService.class, VexScheduleService.class);
    coreServices.register(ListenerService.class, VexListenerService.class);
    coreServices.register(CacheService.class, VexCacheService.class);
    coreServices.register(SignalRegistryService.class, VexSignalRegistryService.class);
    coreServices.register(SignalService.class, VexSignalService.class);
    coreServices.register(ConfigurationService.class, VexConfigurationService.class);
    coreServices.register(ThemeColorService.class, VexThemeColorService.class);
    coreServices.register(MessageCodecService.class, VexMessageCodecService.class);
    coreServices.register(
        MessageTransportService.class,
        VexPaperMessageTransportService.class
    );
    coreServices.registerQueuedServices();
    modules.enable(new PlayerModule(this));
    coreServices.register(DataService.class, VexDataService.class);
    coreServices.register(PlayerContainerService.class, VexPlayerContainerService.class);
    coreServices.registerQueuedServices();
    modules.enable(new LocalizationModule());
    modules.enable(new PacketModule(this));
    modules.enable(new DialogModule());
    modules.enable(new ItemModule());
    coreServices.register(LocalizationService.class, VexLocalizationService.class);
    coreServices.register(SendMessageService.class, VexSendMessageService.class);
    coreServices.register(MessagingService.class, VexMessagingService.class);
    coreServices.register(ProxyPingService.class, VexProxyPingService.class);
    coreServices.register(
        ServerPerformanceService.class,
        VexServerPerformanceService.class
    );
    coreServices.register(
        PerformanceBossBarService.class,
        VexPerformanceBossBarService.class
    );
    coreServices.register(CommandService.class, VexCommandService.class);
    coreServices.register(DialogService.class, VexDialogService.class);
    coreServices.register(ItemService.class, VexItemService.class);
    coreServices.register(PluginBootstrapService.class, VexPluginBootstrapService.class);
    coreServices.register(PaperPlayerService.class, VexPaperPlayerService.class);
    coreServices.register(TextDisplayPacketService.class, VexTextDisplayPacketService.class);
    coreServices.register(ItemDisplayPacketService.class, VexItemDisplayPacketService.class);
    coreServices.register(
        DisplayPassengerPacketService.class,
        VexDisplayPassengerPacketService.class
    );
    coreServices.register(InteractableHologramService.class, VexInteractableHologramService.class);
    coreServices.register(MobHitPacketService.class, VexMobHitPacketService.class);
    coreServices.register(MobGlowPacketService.class, VexMobGlowPacketService.class);
    coreServices.register(LightningPacketService.class, VexLightningPacketService.class);
    coreServices.register(FakeItemMetaService.class, VexFakeItemMetaService.class);
    coreServices.registerQueuedServices();
    coreServices.require(MessagingService.class).register(VexProxyPingResponseHandler.class);
    coreServices.require(DataService.class).register(VexCorePlayerData.class);
    coreServices.require(CommandService.class).register(VexCoreCommand.class);
    initialized = true;
  }

  @Override
  public void onEnable() {
    if (!initialized) {
      getLogger().severe("VexCore cannot be enabled because its loading phase failed");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    modules.startAll();
    coreServices.require(MessageTransportService.class).start();
    coreServices.require(ServerPerformanceService.class).start();
    coreServices.require(PerformanceBossBarService.class).start();
    coreServices.require(ListenerService.class).register(VexPerformanceBossBarListener.class);
    PlatformService platform = services.require(PlatformService.class);
    PlayerDataCoordinatorService players = services.require(PlayerDataCoordinatorService.class);
    coreServices.require(ListenerService.class).register(VexPlayerLifecycleListener.class);
    getServer().getOnlinePlayers().forEach(player -> {
      VexPlayer vexPlayer = players.create(player.getUniqueId(), player.getName());
      vexPlayer.bindPlatformPlayer(player);
    });
    playerAutosaveTask = getServer().getAsyncScheduler().runAtFixedRate(
        this,
        task -> players.saveAll().exceptionally(throwable -> {
          getLogger().log(Level.SEVERE, "Unable to autosave Vex players", throwable);
          return null;
        }),
        5,
        5,
        TimeUnit.MINUTES
    );
    getLogger().info("VexCore successfully enabled on " + platform.getPlatform());
  }

  @Override
  public void onDisable() {
    if (playerAutosaveTask != null) {
      playerAutosaveTask.cancel();
    }
    if (services != null) {
      services.find(PlayerDataCoordinatorService.class).ifPresent(players -> {
        try {
          players.saveAll().join();
        } catch (RuntimeException exception) {
          getLogger().log(Level.SEVERE, "Unable to save every VexPlayer during shutdown", exception);
        }
      });
    }
    if (modules != null) {
      if (coreServices != null) {
        coreServices.unregisterOwnedServices();
      }
      modules.disableAll();
    }
    getServer().getServicesManager().unregisterAll(this);
  }

  @Override
  public @NonNull VexLogger getLogger() {
    if (logger == null) {
      logger = new VexLogger(
          getName(),
          "<dark_gray>[<gradient:#8A2BE2:#00BFFF>VexCore</gradient>]</dark_gray> "
      );
    }
    return logger;
  }

  @Override
  public String getServiceOwnerName() {
    return getName();
  }

  @Override
  public Path getLocalizationDirectory() {
    return getConfigurationDirectory().resolve("languages");
  }

  @Override
  public Path getConfigurationDirectory() {
    Path pluginsDirectory = getDataFolder().toPath().toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getName()).normalize();
  }

  @Override
  public Collection<String> getLocalizationResources() {
    return LocalizationResourceScanner.scan(getFile().toPath());
  }

  @Override
  public Optional<InputStream> getLocalizationResource(final String resourcePath) {
    return Optional.ofNullable(getResource(resourcePath));
  }

  @Override
  public Optional<InputStream> getConfigurationResource(final String resourcePath) {
    return Optional.ofNullable(getResource(resourcePath));
  }

  @Override
  public String getMessagePrefixKey() {
    return "general.prefix";
  }

  @Override
  public void reportLocalizationWarning(final String message, final Throwable cause) {
    reportWarning(message, cause);
  }

  @Override
  public void reportConfigurationWarning(final String message, final Throwable cause) {
    reportWarning(message, cause);
  }

  private void reportWarning(final String message, final Throwable cause) {
    if (cause == null) {
      getLogger().warning(message);
    } else {
      getLogger().log(Level.WARNING, message, cause);
    }
  }
}
