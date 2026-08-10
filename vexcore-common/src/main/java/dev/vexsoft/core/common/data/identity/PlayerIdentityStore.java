package dev.vexsoft.core.common.data.identity;

import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persists the network-wide player identity index. */
public interface PlayerIdentityStore {

  /** Creates missing identity storage structures. */
  CompletableFuture<Void> reconcilePlayerIdentities();

  /** Stores the most recently observed player name. */
  CompletableFuture<PlayerIdentity> recordPlayerIdentity(UUID uniqueId, String name);

  /** Finds one identity by UUID. */
  CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(UUID uniqueId);

  /** Finds one identity by normalized name. */
  CompletableFuture<Optional<PlayerIdentity>> findPlayerIdentity(String name);
}
