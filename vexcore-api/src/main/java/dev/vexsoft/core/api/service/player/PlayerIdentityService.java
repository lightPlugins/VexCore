package dev.vexsoft.core.api.service.player;

import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Resolves current and previously seen player identities independent of plugin data owners. */
public interface PlayerIdentityService extends VexService {

  /** Records a player's current name. */
  CompletableFuture<PlayerIdentity> record(UUID uniqueId, String name);

  /** Finds a stored identity by UUID. */
  CompletableFuture<Optional<PlayerIdentity>> find(UUID uniqueId);

  /** Finds a stored identity by case-insensitive player name. */
  CompletableFuture<Optional<PlayerIdentity>> find(String name);
}
