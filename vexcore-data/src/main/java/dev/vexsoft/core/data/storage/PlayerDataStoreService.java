package dev.vexsoft.core.data.storage;

import dev.vexsoft.core.api.service.VexService;

public interface PlayerDataStoreService extends VexService {

  /** Returns the configured player data store */
  public PlayerDataStore getStore();
}
