package dev.vexsoft.core.paper.platform;

import dev.vexsoft.core.api.service.VexService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface PlatformService extends VexService {

  /** Returns the platform used by the current server */
  public ServerPlatform platform();

  /** Checks whether the current server is running Folia */
  public boolean isFolia();

  /** Checks whether the current thread owns the specified location */
  public boolean isOwnedByCurrentRegion(Location location);

  /** Checks whether the current thread owns the specified entity */
  public boolean isOwnedByCurrentRegion(Entity entity);

  /** Checks whether the current thread owns the global region */
  public boolean isGlobalTickThread();
}
