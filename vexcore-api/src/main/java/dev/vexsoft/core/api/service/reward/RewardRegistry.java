package dev.vexsoft.core.api.service.reward;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.reward.Reward;

/** Registers owner-scoped handlers for keys inside a {@code rewards} section. */
public interface RewardRegistry extends VexService {

  /** Registers a reward implementation under a globally unique configuration key. */
  void register(String key, Class<? extends Reward> rewardType);

  /** Removes a key when it belongs to this service owner. */
  boolean unregister(String key);
}
