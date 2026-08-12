package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates registered containers, cached players and persistence operations
 */
public interface PlayerDataCoordinatorService extends VexService {

  /** Registers every container declared by a plugin data definition */
  void register(ServiceOwner owner, PlayerDataDefinition definition);

  /** Registers a player feature container owned by a plugin. */
  <T extends PlayerContainer> void registerContainer(
      ServiceOwner owner,
      Class<T> type,
      PlayerContainerFactory<? extends T> factory
  );

  /** Removes and closes every player feature container registered by an owner. */
  void unregisterContainers(ServiceOwner owner);

  /** Creates or refreshes a cached player with every registered container */
  VexPlayer create(UUID uniqueId, String name);

  /** Loads a player and places it in the shared cache */
  CompletableFuture<VexPlayer> load(UUID uniqueId, String name);

  /** Finds a player in the shared online cache */
  Optional<VexPlayer> find(UUID uniqueId);

  /** Removes a player from the shared online cache */
  Optional<VexPlayer> remove(UUID uniqueId);

  /** Saves every changed container before removing a cached player */
  CompletableFuture<Void> saveAndRemove(UUID uniqueId);

  /** Saves every changed container while retaining the cached player. */
  CompletableFuture<Void> save(UUID uniqueId);

  /** Saves every changed container of every cached player */
  CompletableFuture<Void> saveAll();

  /** Returns all container keys registered by an owner */
  Collection<DataContainerKey<?>> getKeys(ServiceOwner owner);

  /** Returns the user-facing names of every registered persistent container. */
  Collection<String> getContainerIds();

  /** Returns the number of registered player-facing feature containers. */
  int getFeatureContainerCount();

  /** Resets one registered container for one player. */
  CompletableFuture<Void> resetPlayerContainer(UUID uniqueId, String containerId);

  /** Resets every registered container for one player. */
  CompletableFuture<Void> resetPlayerContainers(UUID uniqueId);

  /** Resets one registered container for all stored and loaded players. */
  CompletableFuture<Void> resetGlobalContainer(String containerId);

  /** Resets every registered container for all stored and loaded players. */
  CompletableFuture<Void> resetGlobalContainers();

  /** Resolves a loaded or stored player identity from a name or UUID. */
  CompletableFuture<Optional<UUID>> resolveUniqueId(String player);
}
