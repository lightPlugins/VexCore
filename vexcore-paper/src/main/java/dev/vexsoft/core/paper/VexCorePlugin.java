package dev.vexsoft.core.paper;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.localization.LocalizationService;
import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.paper.module.ModuleManager;
import dev.vexsoft.core.paper.module.PlatformModule;
import dev.vexsoft.core.paper.module.PlayerModule;
import dev.vexsoft.core.paper.module.LocalizationModule;
import dev.vexsoft.core.paper.listener.VexPlayerLifecycleListener;
import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.plugin.VexLogger;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import dev.vexsoft.core.command.CommandService;
import dev.vexsoft.core.command.VexCommandService;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.localization.VexCorePlayerData;
import dev.vexsoft.core.localization.VexLocalizationService;
import dev.vexsoft.core.paper.command.VexCoreCommand;
import dev.vexsoft.core.paper.localization.LocalizationResourceScanner;
import dev.vexsoft.core.paper.message.SendMessageService;
import dev.vexsoft.core.paper.message.VexSendMessageService;
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

public final class VexCorePlugin extends JavaPlugin implements LocalizationOwner {
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
    modules = new ModuleManager(services);
    modules.enable(new PlatformModule());
    modules.enable(new PlayerModule(this));
    modules.enable(new LocalizationModule());
    coreServices = services.scoped(this);
    coreServices.register(DataService.class, VexDataService.class);
    coreServices.register(LocalizationService.class, VexLocalizationService.class);
    coreServices.register(SendMessageService.class, VexSendMessageService.class);
    coreServices.register(CommandService.class, VexCommandService.class);
    coreServices.registerQueuedServices();
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
    PlatformService platform = services.require(PlatformService.class);
    PlayerDataCoordinatorService players = services.require(PlayerDataCoordinatorService.class);
    getServer().getPluginManager().registerEvents(
        new VexPlayerLifecycleListener(players, getLogger()),
        this
    );
    getServer().getOnlinePlayers().forEach(player -> players.create(
        player.getUniqueId(),
        player.getName()
    ));
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
    Path pluginsDirectory = getDataFolder().toPath().toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve(getName()).resolve("languages").normalize();
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
  public String getMessagePrefixKey() {
    return "general.prefix";
  }

  @Override
  public void reportLocalizationWarning(final String message, final Throwable cause) {
    if (cause == null) {
      getLogger().warning(message);
    } else {
      getLogger().log(Level.WARNING, message, cause);
    }
  }
}
