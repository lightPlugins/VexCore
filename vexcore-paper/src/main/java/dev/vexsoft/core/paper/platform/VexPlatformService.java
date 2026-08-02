package dev.vexsoft.core.paper.platform;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import java.util.Objects;

@Dependencies
public final class VexPlatformService implements PlatformService {

  public VexPlatformService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public ServerPlatform platform() {
    return PlatformDetector.platform();
  }

  @Override
  public boolean isFolia() {
    return PlatformDetector.isFolia();
  }

  @Override
  public boolean isOwnedByCurrentRegion(final Location location) {
    return Bukkit.isOwnedByCurrentRegion(Objects.requireNonNull(location, "location"));
  }

  @Override
  public boolean isOwnedByCurrentRegion(final Entity entity) {
    return Bukkit.isOwnedByCurrentRegion(Objects.requireNonNull(entity, "entity"));
  }

  @Override
  public boolean isGlobalTickThread() {
    return Bukkit.isGlobalTickThread();
  }
}
