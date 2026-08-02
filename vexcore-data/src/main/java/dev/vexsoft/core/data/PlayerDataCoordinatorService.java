package dev.vexsoft.core.data;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataCoordinatorService extends VexService {

  /** Registers every container declared by a plugin data definition */
  public void register(ServiceOwner owner, PlayerDataDefinition definition);

  /** Creates or refreshes a cached player with every registered container */
  public VexPlayer create(UUID uniqueId, String name);

  /** Loads a player and places it in the shared cache */
  public CompletableFuture<VexPlayer> load(UUID uniqueId, String name);

  /** Finds a player in the shared online cache */
  public Optional<VexPlayer> find(UUID uniqueId);

  /** Removes a player from the shared online cache */
  public Optional<VexPlayer> remove(UUID uniqueId);

  /** Saves every changed container before removing a cached player */
  public CompletableFuture<Void> saveAndRemove(UUID uniqueId);

  /** Saves every changed container of every cached player */
  public CompletableFuture<Void> saveAll();

  /** Returns all container keys registered by an owner */
  public Collection<DataContainerKey<?>> getKeys(ServiceOwner owner);
}
