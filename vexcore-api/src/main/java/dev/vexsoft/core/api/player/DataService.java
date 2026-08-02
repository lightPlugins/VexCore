package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.service.VexService;

/**
 * Registers player data definitions owned by the current plugin
 */
public interface DataService extends VexService {

  /** Registers a player data definition owned by this plugin */
  public void register(Class<? extends PlayerDataDefinition> definitionType);
}
