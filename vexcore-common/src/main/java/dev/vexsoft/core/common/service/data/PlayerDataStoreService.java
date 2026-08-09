package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.common.data.storage.PlayerDataStore;

import dev.vexsoft.core.api.service.registry.VexService;

/**
 * Provides the player data store configured for VexCore
 */
public interface PlayerDataStoreService extends VexService {

  /** Returns the configured player data store */
  PlayerDataStore getStore();

  /** Returns the configured storage backend name for diagnostics. */
  default String getStorageType() {
    return getStore().getClass().getSimpleName();
  }
}
