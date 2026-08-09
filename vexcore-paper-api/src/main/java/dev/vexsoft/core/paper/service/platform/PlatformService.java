package dev.vexsoft.core.paper.service.platform;

import dev.vexsoft.core.paper.platform.ServerPlatform;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Exposes the server platform detected by VexCore
 */
public interface PlatformService extends VexService {

  /** Returns the platform used by the current server */
  ServerPlatform getPlatform();

  /** Checks whether the current server is running Folia */
  boolean isFolia();

  /** Checks whether the current thread owns the specified location */
  boolean isOwnedByCurrentRegion(Location location);

  /** Checks whether the current thread owns the specified entity */
  boolean isOwnedByCurrentRegion(Entity entity);

  /** Checks whether the current thread owns the global region */
  boolean isGlobalTickThread();
}
