package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.service.VexService;

public interface DataService extends VexService {

  /** Registers a player data definition owned by this plugin */
  public void register(Class<? extends PlayerDataDefinition> definitionType);
}
