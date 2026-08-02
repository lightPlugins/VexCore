package dev.vexsoft.core.paper;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.paper.module.ModuleManager;
import dev.vexsoft.core.paper.module.PlatformModule;
import dev.vexsoft.core.paper.module.PlayerModule;
import dev.vexsoft.core.paper.listener.VexPlayerLifecycleListener;
import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.plugin.VexLogger;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class VexCorePlugin extends JavaPlugin {
  private DefaultServiceRegistry services;
  private ModuleManager modules;
  private VexLogger logger;
  private ScheduledTask playerAutosaveTask;

  @Override
  public void onLoad() {
    services = new DefaultServiceRegistry();
    getServer().getServicesManager().register(ServiceRegistry.class, services, this, ServicePriority.Normal);
    modules = new ModuleManager(services);
    modules.enable(new PlatformModule());
    modules.enable(new PlayerModule(this));
  }

  @Override
  public void onEnable() {
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
}
