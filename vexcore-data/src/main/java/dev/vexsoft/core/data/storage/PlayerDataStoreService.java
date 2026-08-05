package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.service.VexService;

/**
 * Provides the player data store configured for VexCore
 */
public interface PlayerDataStoreService extends VexService {

  /** Returns the configured player data store */
  PlayerDataStore getStore();
}
