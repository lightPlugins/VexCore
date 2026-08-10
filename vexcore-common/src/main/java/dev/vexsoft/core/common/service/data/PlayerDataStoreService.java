package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.global.GlobalDataStore;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;

import dev.vexsoft.core.api.service.registry.VexService;

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

  /** Returns the configured storage backend name for diagnostics. */
  default String getStorageType() {
    return getStore().getClass().getSimpleName();
  }
}
