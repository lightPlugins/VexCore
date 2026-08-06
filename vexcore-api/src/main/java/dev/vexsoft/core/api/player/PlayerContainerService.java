package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.service.VexService;

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
