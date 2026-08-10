package dev.vexsoft.core.velocity.service.directory;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryRequest;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryResponse;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.Objects;

/** Resolves online player locations from Velocity's authoritative connection state. */
@Dependencies(MessagingService.class)
public final class VexPlayerDirectoryRequestHandler implements
    MessageHandler<PlayerDirectoryRequest> {

  private final ProxyServer proxy;
  private final MessagingService messages;

  public VexPlayerDirectoryRequestHandler(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof VexCoreVelocityPlugin plugin)) {
      throw new IllegalArgumentException("Directory handler must be owned by VexCore");
    }
    proxy = plugin.getProxyServer();
    messages = checkedServices.require(MessagingService.class);
  }

  @Override
  public MessageType<PlayerDirectoryRequest> getMessageType() {
    return PlayerDirectoryMessages.REQUEST;
  }

  @Override
  public void handle(final PlayerDirectoryRequest request, final MessageContext context) {
    if (context.getSourceServer().isBlank()) {
      return;
    }
    String serverId = proxy.getPlayer(request.playerId())
        .flatMap(player -> player.getCurrentServer())
        .map(connection -> connection.getServerInfo().getName())
        .orElse("");
    messages.send(
        MessageTarget.server(context.getSourceServer()),
        PlayerDirectoryMessages.RESPONSE,
        new PlayerDirectoryResponse(request.requestId(), request.playerId(), serverId)
    );
  }
}
