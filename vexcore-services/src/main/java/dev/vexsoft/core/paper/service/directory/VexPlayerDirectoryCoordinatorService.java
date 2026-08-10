package dev.vexsoft.core.paper.service.directory;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryRequest;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Default request coordinator for Velocity-backed player lookups. */
@Dependencies(MessagingService.class)
public final class VexPlayerDirectoryCoordinatorService implements
    PlayerDirectoryCoordinatorService,
    AutoCloseable {

  private static final long LOOKUP_TIMEOUT_SECONDS = 5L;

  private final MessagingService messages;
  private final ConcurrentHashMap<UUID, CompletableFuture<Optional<NetworkPlayer>>> pending =
      new ConcurrentHashMap<>();

  public VexPlayerDirectoryCoordinatorService(final VexServiceRegistry services) {
    messages = Objects.requireNonNull(services, "services").require(MessagingService.class);
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
  public void complete(final PlayerDirectoryResponse response) {
    PlayerDirectoryResponse checkedResponse = Objects.requireNonNull(response, "response");
    CompletableFuture<Optional<NetworkPlayer>> result = pending.remove(
        checkedResponse.requestId()
    );
    if (result == null) {
      return;
    }
    Optional<NetworkPlayer> player = checkedResponse.serverId().isBlank()
        ? Optional.empty()
        : Optional.of(new NetworkPlayer(
            checkedResponse.playerId(),
            new ServerId(checkedResponse.serverId())
        ));
    result.complete(player);
  }

  @Override
  public void close() {
    pending.values().forEach(future -> future.complete(Optional.empty()));
    pending.clear();
  }
}
