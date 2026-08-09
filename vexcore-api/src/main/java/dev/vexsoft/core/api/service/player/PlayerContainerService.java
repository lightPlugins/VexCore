package dev.vexsoft.core.api.service.player;

import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;

import dev.vexsoft.core.api.service.registry.VexService;

/** Registers player-bound feature containers supplied by the current service owner. */
public interface PlayerContainerService extends VexService {

  /**
   * Registers one container type and its per-player factory.
   *
   * @param type public container API
   * @param factory factory invoked once for every loaded player session
   */
  <T extends PlayerContainer> void register(
      Class<T> type,
      PlayerContainerFactory<? extends T> factory
  );
}
