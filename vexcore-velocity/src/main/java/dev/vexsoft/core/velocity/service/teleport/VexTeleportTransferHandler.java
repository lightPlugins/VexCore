package dev.vexsoft.core.velocity.service.teleport;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TeleportStatus;
import dev.vexsoft.core.common.messaging.teleport.TeleportArrival;
import dev.vexsoft.core.common.messaging.teleport.TeleportCompletion;
import dev.vexsoft.core.common.messaging.teleport.TeleportMessages;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.Objects;

/** Transfers requested players and forwards their exact destination to the target backend. */
@Dependencies(MessagingService.class)
public final class VexTeleportTransferHandler implements
    MessageHandler<TeleportTransferRequest> {

  private final ProxyServer proxy;
  private final MessagingService messages;

  public VexTeleportTransferHandler(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof VexCoreVelocityPlugin plugin)) {
      throw new IllegalArgumentException("Teleport transfer handler must be owned by VexCore");
    }
    proxy = plugin.getProxyServer();
    messages = checkedServices.require(MessagingService.class);
  }

  @Override
  public MessageType<TeleportTransferRequest> getMessageType() {
    return TeleportMessages.TRANSFER_REQUEST;
  }

  @Override
  public void handle(final TeleportTransferRequest request, final MessageContext context) {
    String sourceServer = context.getSourceServer();
    if (sourceServer.isBlank()) {
      return;
    }
    Player player = proxy.getPlayer(request.playerId()).orElse(null);
    if (player == null) {
      reject(request, sourceServer, TeleportStatus.PLAYER_OFFLINE, "The player is not online");
      return;
    }
    RegisteredServer destination = proxy.getServer(request.destination().server().value())
        .orElse(null);
    if (destination == null) {
      reject(
          request,
          sourceServer,
          TeleportStatus.SERVER_UNAVAILABLE,
          "The destination server is not registered on the proxy"
      );
      return;
    }
    if (player.getCurrentServer()
        .map(connection -> connection.getServer().equals(destination))
        .orElse(false)) {
      forwardArrival(request, sourceServer);
      return;
    }
    player.createConnectionRequest(destination).connect().whenComplete((result, throwable) -> {
      if (throwable != null || !result.isSuccessful()) {
        reject(
            request,
            sourceServer,
            TeleportStatus.TRANSFER_REJECTED,
            "Velocity could not connect the player to the destination server"
        );
        return;
      }
      forwardArrival(request, sourceServer);
    });
  }

  private void forwardArrival(
      final TeleportTransferRequest request,
      final String sourceServer
  ) {
    DeliveryResult delivery = messages.send(
        MessageTarget.server(request.destination().server().value()),
        TeleportMessages.ARRIVAL,
        new TeleportArrival(
            request.requestId(),
            request.playerId(),
            request.destination(),
            sourceServer
        )
    );
    if (delivery != DeliveryResult.SENT && delivery != DeliveryResult.QUEUED) {
      reject(
          request,
          sourceServer,
          TeleportStatus.SERVER_UNAVAILABLE,
          "The destination server did not accept the teleport"
      );
    }
  }

  private void reject(
      final TeleportTransferRequest request,
      final String sourceServer,
      final TeleportStatus status,
      final String message
  ) {
    messages.send(
        MessageTarget.server(sourceServer),
        TeleportMessages.COMPLETION,
        new TeleportCompletion(request.requestId(), status, message)
    );
  }
}
