package dev.vexsoft.core.paper.service.messaging;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.messaging.MessageCodecService;
import dev.vexsoft.core.common.service.messaging.MessageEnvelope;
import dev.vexsoft.core.common.service.messaging.MessageTransportService;
import dev.vexsoft.core.common.service.messaging.RedisMessageChannel;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.common.data.redis.RedisPlayerDataStore;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import java.nio.file.Path;
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

/** Exchanges encoded messages with Velocity through Paper's plugin messaging channels. */
@Dependencies({MessageCodecService.class, PlayerDataStoreService.class, ConfigurationService.class})
public final class VexPaperMessageTransportService implements MessageTransportService, PluginMessageListener,
    AutoCloseable {

    private static final String CHANNEL = "vexcore:messaging";

    private final JavaPlugin plugin;
    private final MessageCodecService codec;
    private final RedisMessageChannel redis;
    private final CopyOnWriteArrayList<Consumer<MessageEnvelope>> receivers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();

    public VexPaperMessageTransportService(final VexServiceRegistry services) {
        VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");

        if (!(checkedServices.getOwner() instanceof JavaPlugin javaPlugin)) {
            throw new IllegalArgumentException("Paper message transport owner must be a JavaPlugin");
        }

        this.plugin = javaPlugin;
        this.codec = checkedServices.require(MessageCodecService.class);
        var backend = checkedServices.require(PlayerDataStoreService.class).getStore();
        if (backend instanceof RedisPlayerDataStore live) {
            String serverId = checkedServices.require(ConfigurationService.class)
                .load(Path.of("network.yml"), "network.yml").getString("server-id", "server");
            redis = new RedisMessageChannel(live, codec, false, serverId,
                playerId -> plugin.getServer().getPlayer(playerId) != null);
            redis.subscribe(message -> receivers.forEach(receiver -> receiver.accept(message)));
        } else {
            redis = null;
        }
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        if (redis != null) {
            redis.start();
            return;
        }

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public DeliveryResult send(final MessageEnvelope message) {
        if (redis != null) {
            return redis.send(message);
        }
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

        carrier.getScheduler().run(plugin, task -> carrier.sendPluginMessage(plugin, CHANNEL, encoded), null);

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
        if (redis != null) {
            redis.close();
            receivers.clear();
            return;
        }
        receivers.clear();

        if (!started.compareAndSet(true, false)) {
            return;
        }

        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }
}
