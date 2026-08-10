package dev.vexsoft.core.paper.service.directory;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryRequest;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListRequest;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListResponse;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Default request coordinator for Velocity-backed player lookups. */
@Dependencies({CacheService.class, MessagingService.class})
public final class VexPlayerDirectoryCoordinatorService implements
    PlayerDirectoryCoordinatorService,
    AutoCloseable {

  private static final long LOOKUP_TIMEOUT_SECONDS = 5L;
  private static final String ONLINE_PLAYERS_KEY = "all";

  private final MessagingService messages;
  private final VexAsyncCache<String, List<NetworkPlayer>> onlinePlayerSnapshots;
  private final ConcurrentHashMap<UUID, CompletableFuture<Optional<NetworkPlayer>>> pending =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, CompletableFuture<List<NetworkPlayer>>> pendingLists =
      new ConcurrentHashMap<>();
  private volatile List<NetworkPlayer> lastOnlinePlayers = List.of();

  public VexPlayerDirectoryCoordinatorService(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    messages = checked.require(MessagingService.class);
    onlinePlayerSnapshots = checked.require(CacheService.class).createAsync(
        "network-online-players",
        VexCacheOptions.builder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofSeconds(2))
            .build(),
        ignored -> requestOnlinePlayers()
    );
  }

  @Override
  public CompletableFuture<Optional<NetworkPlayer>> find(final UUID uniqueId) {
    UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    UUID requestId = UUID.randomUUID();
    CompletableFuture<Optional<NetworkPlayer>> result = new CompletableFuture<>();
    pending.put(requestId, result);
    DeliveryResult delivery = messages.send(
        MessageTarget.proxy(),
        PlayerDirectoryMessages.REQUEST,
        new PlayerDirectoryRequest(requestId, checkedUniqueId)
    );
    if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
      pending.remove(requestId);
      return CompletableFuture.completedFuture(Optional.empty());
    }
    return result.completeOnTimeout(Optional.empty(), LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .whenComplete((ignored, throwable) -> pending.remove(requestId));
  }

  @Override
  public List<NetworkPlayer> getOnlinePlayers() {
    onlinePlayerSnapshots.get(ONLINE_PLAYERS_KEY);
    return lastOnlinePlayers;
  }

  @Override
  public void complete(final PlayerDirectoryResponse response) {
    PlayerDirectoryResponse checkedResponse = Objects.requireNonNull(response, "response");
    CompletableFuture<Optional<NetworkPlayer>> result = pending.remove(
        checkedResponse.requestId()
    );
    if (result == null) {
      return;
    }
    Optional<NetworkPlayer> player = checkedResponse.serverId().isBlank()
        || checkedResponse.playerName().isBlank()
        ? Optional.empty()
        : Optional.of(new NetworkPlayer(
            checkedResponse.playerId(),
            checkedResponse.playerName(),
            new ServerId(checkedResponse.serverId())
        ));
    result.complete(player);
  }

  @Override
  public void complete(final PlayerDirectoryListResponse response) {
    PlayerDirectoryListResponse checkedResponse = Objects.requireNonNull(response, "response");
    CompletableFuture<List<NetworkPlayer>> result = pendingLists.remove(
        checkedResponse.requestId()
    );
    if (result != null) {
      result.complete(checkedResponse.players());
    }
  }

  private CompletableFuture<List<NetworkPlayer>> requestOnlinePlayers() {
    UUID requestId = UUID.randomUUID();
    CompletableFuture<List<NetworkPlayer>> result = new CompletableFuture<>();
    pendingLists.put(requestId, result);
    DeliveryResult delivery = messages.send(
        MessageTarget.proxy(),
        PlayerDirectoryMessages.LIST_REQUEST,
        new PlayerDirectoryListRequest(requestId)
    );
    if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
      pendingLists.remove(requestId);
      return CompletableFuture.completedFuture(lastOnlinePlayers);
    }
    return result.completeOnTimeout(
        lastOnlinePlayers,
        LOOKUP_TIMEOUT_SECONDS,
        TimeUnit.SECONDS
    ).thenApply(players -> {
      lastOnlinePlayers = List.copyOf(players);
      return lastOnlinePlayers;
    }).whenComplete((ignored, throwable) -> pendingLists.remove(requestId));
  }

  @Override
  public void close() {
    pending.values().forEach(future -> future.complete(Optional.empty()));
    pending.clear();
    pendingLists.values().forEach(future -> future.complete(lastOnlinePlayers));
    pendingLists.clear();
    onlinePlayerSnapshots.invalidateAll();
    lastOnlinePlayers = List.of();
  }
}
