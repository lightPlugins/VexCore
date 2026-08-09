package dev.vexsoft.core.velocity.service.messaging;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.messaging.MessageCodecService;
import dev.vexsoft.core.common.service.messaging.MessageEnvelope;
import dev.vexsoft.core.common.service.messaging.MessageTransportService;
import dev.vexsoft.core.velocity.VexCoreVelocityPlugin;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;

@Dependencies({MessageCodecService.class})
public final class VexVelocityMessageTransportService implements
    MessageTransportService,
    AutoCloseable {

  private static final MinecraftChannelIdentifier CHANNEL =
      MinecraftChannelIdentifier.create("vexcore", "messaging");
  private static final int MAX_PENDING_PER_SERVER = 256;

  private final VexCoreVelocityPlugin plugin;
  private final ProxyServer proxyServer;
  private final Logger logger;
  private final MessageCodecService codec;
  private final CopyOnWriteArrayList<Consumer<MessageEnvelope>> receivers =
      new CopyOnWriteArrayList<>();
  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<MessageEnvelope>> pending =
      new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean();

  public VexVelocityMessageTransportService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof VexCoreVelocityPlugin corePlugin)) {
      throw new IllegalArgumentException("Velocity message transport must be owned by VexCore");
    }
    this.plugin = corePlugin;
    this.proxyServer = corePlugin.getProxyServer();
    this.logger = corePlugin.getPlatformLogger();
    this.codec = checkedServices.require(MessageCodecService.class);
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    proxyServer.getChannelRegistrar().register(CHANNEL);
    proxyServer.getEventManager().register(plugin, this);
  }

  @Override
  public DeliveryResult send(final MessageEnvelope message) {
    return route(Objects.requireNonNull(message, "message"));
  }

  @Override
  public AutoCloseable subscribe(final Consumer<MessageEnvelope> receiver) {
    Consumer<MessageEnvelope> checkedReceiver = Objects.requireNonNull(receiver, "receiver");
    receivers.add(checkedReceiver);
    return () -> receivers.remove(checkedReceiver);
  }

  @Subscribe
  public void onPluginMessage(final PluginMessageEvent event) {
    if (!CHANNEL.equals(event.getIdentifier())) {
      return;
    }
    // Never let a client-originated message leak through as a trusted proxy message
    event.setResult(PluginMessageEvent.ForwardResult.handled());
    if (!(event.getSource() instanceof ServerConnection backend)) {
      return;
    }
    try {
      MessageEnvelope envelope = codec.decode(event.getData()).withSourceServer(
          backend.getServerInfo().getName()
      );
      route(envelope);
    } catch (IllegalArgumentException exception) {
      logger.warn("[VexCore] Ignoring an invalid network message from a backend", exception);
    }
  }

  @Subscribe
  public void onServerPostConnect(final ServerPostConnectEvent event) {
    event.getPlayer().getCurrentServer().ifPresent(connection -> flush(connection.getServer()));
  }

  @Override
  public void close() {
    receivers.clear();
    pending.clear();
    if (started.compareAndSet(true, false)) {
      proxyServer.getEventManager().unregisterListener(plugin, this);
      proxyServer.getChannelRegistrar().unregister(CHANNEL);
    }
  }

  private DeliveryResult route(final MessageEnvelope message) {
    if (message.isExpired(System.currentTimeMillis())) {
      return DeliveryResult.FAILED;
    }
    return switch (message.getTarget().getType()) {
      case PROXY -> dispatch(message);
      case SERVER -> proxyServer.getServer(message.getTarget().getValue())
          .map(server -> deliver(server, message))
          .orElse(DeliveryResult.FAILED);
      case PLAYER -> deliverToPlayer(message);
      case ALL_SERVERS -> deliverToAllServers(message);
    };
  }

  private DeliveryResult deliverToPlayer(final MessageEnvelope message) {
    UUID playerId;
    try {
      playerId = UUID.fromString(message.getTarget().getValue());
    } catch (IllegalArgumentException exception) {
      return DeliveryResult.FAILED;
    }
    return proxyServer.getPlayer(playerId)
        .flatMap(Player::getCurrentServer)
        .map(ServerConnection::getServer)
        .map(server -> deliver(server, message))
        .orElse(DeliveryResult.NO_CONNECTION);
  }

  private DeliveryResult deliverToAllServers(final MessageEnvelope message) {
    DeliveryResult result = DeliveryResult.NO_CONNECTION;
    for (RegisteredServer server : proxyServer.getAllServers()) {
      if (server.getServerInfo().getName().equals(message.getSourceServer())) {
        continue;
      }
      DeliveryResult current = deliver(server, message);
      if (current == DeliveryResult.SENT) {
        result = DeliveryResult.SENT;
      } else if (current == DeliveryResult.QUEUED && result != DeliveryResult.SENT) {
        result = DeliveryResult.QUEUED;
      }
    }
    return result;
  }

  private DeliveryResult deliver(
      final RegisteredServer server,
      final MessageEnvelope message
  ) {
    byte[] encoded;
    try {
      encoded = codec.encode(message);
    } catch (IllegalArgumentException exception) {
      return DeliveryResult.MESSAGE_TOO_LARGE;
    }
    if (server.sendPluginMessage(CHANNEL, encoded)) {
      return DeliveryResult.SENT;
    }
    queue(server.getServerInfo().getName(), message);
    return DeliveryResult.QUEUED;
  }

  private DeliveryResult dispatch(final MessageEnvelope message) {
    for (Consumer<MessageEnvelope> receiver : receivers) {
      try {
        receiver.accept(message);
      } catch (RuntimeException exception) {
        logger.warn("[VexCore] A network message handler failed", exception);
      }
    }
    return DeliveryResult.SENT;
  }

  private void queue(final String serverName, final MessageEnvelope message) {
    ConcurrentLinkedDeque<MessageEnvelope> serverQueue = pending.computeIfAbsent(
        serverName,
        ignored -> new ConcurrentLinkedDeque<>()
    );
    while (serverQueue.size() >= MAX_PENDING_PER_SERVER) {
      serverQueue.pollFirst();
    }
    serverQueue.addLast(message);
  }

  private void flush(final RegisteredServer server) {
    String serverName = server.getServerInfo().getName();
    ConcurrentLinkedDeque<MessageEnvelope> serverQueue = pending.remove(serverName);
    if (serverQueue == null) {
      return;
    }
    MessageEnvelope message;
    while ((message = serverQueue.pollFirst()) != null) {
      if (!message.isExpired(System.currentTimeMillis())) {
        deliver(server, message);
      }
    }
  }
}
