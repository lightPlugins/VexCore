package dev.vexsoft.core.paper;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.paper.module.ModuleManager;
import dev.vexsoft.core.paper.module.PlatformModule;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.plugin.VexLogger;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

public final class VexCorePlugin extends JavaPlugin {
  private DefaultServiceRegistry services;
  private ModuleManager modules;
  private VexLogger logger;

  @Override
  public void onLoad() {
    services = new DefaultServiceRegistry();
    getServer().getServicesManager().register(ServiceRegistry.class, services, this, ServicePriority.Normal);
    modules = new ModuleManager(services);
    modules.enable(new PlatformModule());
  }

  @Override
  public void onEnable() {
    PlatformService platform = services.require(PlatformService.class);
    getLogger().info("VexCore successfully enabled on " + platform.platform());
  }

  @Override
  public void onDisable() {
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
