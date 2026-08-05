package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.service.VexService;

/**
 * Registers persistent player-data definitions owned by the current plugin scope.
 *
 * <p>Registration reconciles the owner's storage schema before returning. New keys are installed
 * with defaults on players that are already loaded.</p>
 */
public interface DataService extends VexService {

  /**
   * Creates and registers a player-data definition owned by this plugin.
   *
   * @param definitionType public definition class created through the scoped service registry
   * @throws IllegalStateException if registration or schema reconciliation fails
   */
  void register(Class<? extends PlayerDataDefinition> definitionType);
}
