package dev.vexsoft.core.api.service.player;

import dev.vexsoft.core.api.player.VexPlayer;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides access to fully loaded {@link VexPlayer} instances in the shared online cache.
 *
 * <p>Players enter the cache during the asynchronous pre-login phase and are removed after their
 * disconnect save completes. This service does not initiate storage loads.</p>
 */
public interface PlayerService extends VexService {

  /**
   * Finds a currently loaded Vex player.
   *
   * @param uniqueId player identity
   * @return the cached player, or an empty optional while unavailable
   */
  Optional<VexPlayer> find(UUID uniqueId);

  /**
   * Returns a currently loaded Vex player or fails when it is unavailable.
   *
   * @param uniqueId player identity
   * @return the cached player
   * @throws IllegalStateException if the player is not currently loaded
   */
  VexPlayer require(UUID uniqueId);

}
