package dev.vexsoft.core.paper.service.messaging;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.messaging.MessageCodecService;
import dev.vexsoft.core.common.service.messaging.MessageEnvelope;
import dev.vexsoft.core.common.service.messaging.MessageTransportService;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

@Dependencies({MessageCodecService.class})
public final class VexPaperMessageTransportService implements
    MessageTransportService,
    PluginMessageListener,
    AutoCloseable {

  private static final String CHANNEL = "vexcore:messaging";

  private final JavaPlugin plugin;
  private final MessageCodecService codec;
  private final CopyOnWriteArrayList<Consumer<MessageEnvelope>> receivers =
      new CopyOnWriteArrayList<>();
  private final AtomicBoolean started = new AtomicBoolean();

  public VexPaperMessageTransportService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof JavaPlugin javaPlugin)) {
      throw new IllegalArgumentException("Paper message transport owner must be a JavaPlugin");
    }
    this.plugin = javaPlugin;
    this.codec = checkedServices.require(MessageCodecService.class);
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
  }

  @Override
  public DeliveryResult send(final MessageEnvelope message) {
    byte[] encoded;
    try {
      encoded = codec.encode(Objects.requireNonNull(message, "message"));
    } catch (IllegalArgumentException exception) {
      return DeliveryResult.MESSAGE_TOO_LARGE;
    }
    Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    if (carrier == null) {
      return DeliveryResult.NO_CONNECTION;
    }
    if (Bukkit.isPrimaryThread()) {
      carrier.sendPluginMessage(plugin, CHANNEL, encoded);
      return DeliveryResult.SENT;
    }
    carrier.getScheduler().run(
        plugin,
        task -> carrier.sendPluginMessage(plugin, CHANNEL, encoded),
        null
    );
    return DeliveryResult.QUEUED;
  }

  @Override
  public AutoCloseable subscribe(final Consumer<MessageEnvelope> receiver) {
    Consumer<MessageEnvelope> checkedReceiver = Objects.requireNonNull(receiver, "receiver");
    receivers.add(checkedReceiver);
    return () -> receivers.remove(checkedReceiver);
  }

  @Override
  public void onPluginMessageReceived(
      final @NonNull String channel,
      final @NonNull Player player,
      final byte @NonNull [] message
  ) {
    if (!CHANNEL.equals(channel)) {
      return;
    }
    try {
      MessageEnvelope envelope = codec.decode(message);
      for (Consumer<MessageEnvelope> receiver : receivers) {
        receiver.accept(envelope);
      }
    } catch (IllegalArgumentException exception) {
      plugin.getLogger().log(Level.WARNING, "Ignoring an invalid VexCore network message", exception);
    }
  }

  @Override
  public void close() {
    receivers.clear();
    if (!started.compareAndSet(true, false)) {
      return;
    }
    plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
  }
}
