package dev.vexsoft.core.api.service.network;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Resolves the backend server currently hosting an online player. */
public interface PlayerDirectoryService extends VexService {

  /** Looks up one player through the Velocity proxy. */
  CompletableFuture<Optional<NetworkPlayer>> find(UUID uniqueId);

  /** Returns a short-lived cached snapshot of players connected through Velocity. */
  List<NetworkPlayer> getOnlinePlayers();
}
