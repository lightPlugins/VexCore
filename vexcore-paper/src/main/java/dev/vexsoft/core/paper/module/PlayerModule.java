package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.data.VexPlayerDataCoordinatorService;
import dev.vexsoft.core.data.VexPlayerService;
import dev.vexsoft.core.data.storage.PlayerDataStoreService;
import dev.vexsoft.core.data.storage.VexPlayerDataStoreService;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class PlayerModule implements VexModule, ConfigurationOwner {

  @NonNull
  private final JavaPlugin plugin;
  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(ConfigurationService.class, VexConfigurationService.class);
    services.register(PlayerDataStoreService.class, VexPlayerDataStoreService.class);
    services.register(PlayerDataCoordinatorService.class, VexPlayerDataCoordinatorService.class);
    services.register(PlayerService.class, VexPlayerService.class);
    services.registerQueuedServices();
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override
  public String getServiceOwnerName() {
    return "VexCore";
  }

  @Override
  public Path getConfigurationDirectory() {
    Path pluginsDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
    if (pluginsDirectory == null) {
      throw new IllegalStateException("Unable to resolve the plugins directory");
    }
    return pluginsDirectory.resolve("VexSoft").resolve("VexCore").normalize();
  }

  @Override
  public Optional<InputStream> getConfigurationResource(final String resourcePath) {
    return Optional.ofNullable(plugin.getResource(resourcePath));
  }

  @Override
  public void reportConfigurationWarning(final String message, final Throwable cause) {
    if (cause == null) {
      plugin.getLogger().warning(message);
    } else {
      plugin.getLogger().log(Level.WARNING, message, cause);
    }
  }
}
