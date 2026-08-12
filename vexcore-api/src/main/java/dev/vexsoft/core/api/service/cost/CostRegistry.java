package dev.vexsoft.core.api.service.cost;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.cost.Cost;

/** Registers owner-scoped handlers for keys inside a {@code costs} section. */
public interface CostRegistry extends VexService {

  /** Registers a cost implementation under a globally unique configuration key. */
  void register(String key, Class<? extends Cost> costType);

  /** Removes a key when it belongs to this service owner. */
  boolean unregister(String key);
}
