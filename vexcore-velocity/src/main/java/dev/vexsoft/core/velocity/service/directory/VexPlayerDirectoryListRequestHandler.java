package dev.vexsoft.core.velocity.service.directory;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.vexsoft.core.api.messaging.MessageContext;
import dev.vexsoft.core.api.messaging.MessageHandler;
import dev.vexsoft.core.api.messaging.MessageTarget;
import dev.vexsoft.core.api.messaging.MessageType;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListRequest;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryListResponse;
import dev.vexsoft.core.common.messaging.directory.PlayerDirectoryMessages;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Supplies authoritative network-player snapshots from Velocity. */
@Dependencies(MessagingService.class)
public final class VexPlayerDirectoryListRequestHandler implements
    MessageHandler<PlayerDirectoryListRequest> {

  private final ProxyServer proxy;
  private final MessagingService messages;

  public VexPlayerDirectoryListRequestHandler(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    if (!(checked.getOwner() instanceof VexCoreVelocityPlugin plugin)) {
      throw new IllegalArgumentException("Directory handler must be owned by VexCore");
    }
    proxy = plugin.getProxyServer();
    messages = checked.require(MessagingService.class);
  }

  @Override
  public MessageType<PlayerDirectoryListRequest> getMessageType() {
    return PlayerDirectoryMessages.LIST_REQUEST;
  }

  @Override
  public void handle(final PlayerDirectoryListRequest request, final MessageContext context) {
    if (context.getSourceServer().isBlank()) {
      return;
    }
    List<NetworkPlayer> players = proxy.getAllPlayers().stream()
        .flatMap(player -> player.getCurrentServer().stream().map(connection ->
            new NetworkPlayer(
                player.getUniqueId(),
                player.getUsername(),
                new ServerId(connection.getServerInfo().getName())
            )
        ))
        .sorted(Comparator.comparing(player -> player.name().toLowerCase(Locale.ROOT)))
        .toList();
    messages.send(
        MessageTarget.server(context.getSourceServer()),
        PlayerDirectoryMessages.LIST_RESPONSE,
        new PlayerDirectoryListResponse(request.requestId(), players)
    );
  }
}
