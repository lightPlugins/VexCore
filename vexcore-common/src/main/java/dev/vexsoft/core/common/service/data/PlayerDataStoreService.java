package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;

import dev.vexsoft.core.api.service.registry.VexService;
import java.time.Duration;

/**
 * Provides the player data store configured for VexCore
 */
public interface PlayerDataStoreService extends VexService {

  /** Returns the configured player data store */
  PlayerDataStore getStore();

  /** Returns the global data store sharing this storage backend's resources. */
  GlobalDataStore getGlobalStore();

  /** Returns the player identity index sharing this storage backend's resources. */
  PlayerIdentityStore getPlayerIdentityStore();

  /** Returns how long a login may wait for persistent player data. */
  default Duration getLoginTimeout() {
    return Duration.ofSeconds(15L);
  }

  /** Returns the message shown when player data cannot be loaded before login. */
  default String getLoginKickMessage() {
    return "Your player data could not be loaded. Please try again.";
  }

  /** Returns the configured storage backend name for diagnostics. */
  default String getStorageType() {
    return getStore().getClass().getSimpleName();
  }
}
