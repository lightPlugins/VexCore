package dev.vexsoft.core.common.service.messaging;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.common.data.redis.RedisPlayerDataStore;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bounded, resumable control messages independent of player plugin-channel connections. */
public final class RedisMessageChannel implements AutoCloseable {

    private static final String RENEW = "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end "
        + "if redis.call('GET', KEYS[2]) == ARGV[2] then redis.call('PEXPIRE', KEYS[2], 15000); return 1 end "
        + "return 0";
    private static final String SEND = "if redis.call('GET', KEYS[1]) ~= ARGV[1] then "
        + "return redis.error_reply('LIVE_EPOCH_CHANGED') end "
        + "if redis.call('GET', KEYS[3]) ~= ARGV[3] then return redis.error_reply('NODE_LEASE_LOST') end "
        + "local id = redis.call('XADD', KEYS[2], 'MAXLEN', '~', 10000, '*', 'payload', ARGV[2]); "
        + "redis.call('EXPIRE', KEYS[2], 120); return id";
    private final RedisPlayerDataStore backend;
    private final MessageCodecService codec;
    private final boolean proxy;
    private final String serverId;
    private final Predicate<UUID> ownsPlayer;
    private final String token = UUID.randomUUID().toString();
    private final CopyOnWriteArrayList<Consumer<MessageEnvelope>> receivers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("VexCore-Redis-messages").factory());
    private final Logger logger = Logger.getLogger(RedisMessageChannel.class.getName());
    private final AtomicBoolean started = new AtomicBoolean();
    private CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);
    private RedisAsyncCommands<String, String> commands;
    private String position;

    public RedisMessageChannel(
        final RedisPlayerDataStore backend, final MessageCodecService codec, final boolean proxy,
        final String serverId, final Predicate<UUID> ownsPlayer
    ) {
        this.backend = backend;
        this.codec = codec;
        this.proxy = proxy;
        this.serverId = serverId;
        this.ownsPlayer = ownsPlayer;
    }

    /** Acquires the endpoint lease and starts reading control messages from this process's own cursor. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ready = backend.messageCommands().thenAcceptAsync(connection -> {
            commands = connection;
            if (!"OK".equals(commands.set(node(), token, SetArgs.Builder.nx().px(15000)).toCompletableFuture().join())) {
                throw new IllegalStateException("Duplicate Redis network endpoint: " + node());
            }
            var tail = commands.xrevrange(stream(), Range.unbounded(), Limit.from(1)).toCompletableFuture().join();
            position = tail.isEmpty() ? "0-0" : tail.getFirst().getId();
            worker.scheduleWithFixedDelay(this::poll, 0, 250, TimeUnit.MILLISECONDS);
        }, worker).whenComplete((ignored, failure) -> {
            if (failure != null) {
                backend.fence(failure);
                logger.log(Level.SEVERE, "Redis messaging could not acquire its unique network endpoint", failure);
            }
        });
    }

    /** Completes after the endpoint is able to receive messages, without blocking the platform thread. */
    public CompletableFuture<Void> readiness() {
        return ready.copy();
    }

    /** Queues a bounded control message; request callers retain their existing timeout and correlation behavior. */
    public DeliveryResult send(final MessageEnvelope message) {
        if (!started.get() || ready.isCompletedExceptionally()) {
            return DeliveryResult.NO_CONNECTION;
        }
        String encoded;
        try {
            encoded = Base64.getEncoder().encodeToString(codec.encode(message.withSourceServer(proxy ? "" : serverId)));
        } catch (IllegalArgumentException failure) {
            return DeliveryResult.MESSAGE_TOO_LARGE;
        }
        ready.thenCompose(ignored -> commands.eval(SEND, ScriptOutputType.VALUE,
            new String[]{backend.namespace() + "epoch", stream(), node()}, backend.liveEpoch(), encoded, token)
            .toCompletableFuture()).exceptionally(failure -> {
                logger.log(Level.WARNING, "Redis control message failed; its request will time out", failure);
                return null;
            });
        return DeliveryResult.QUEUED;
    }

    /** Subscribes a platform receiver to messages addressed to this endpoint. */
    public AutoCloseable subscribe(final Consumer<MessageEnvelope> receiver) {
        receivers.add(receiver);
        return () -> receivers.remove(receiver);
    }

    @Override
    public void close() {
        started.set(false);
        worker.shutdownNow();
        if (commands != null) {
            commands.eval("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
                ScriptOutputType.INTEGER, new String[]{node()}, token);
        }
        receivers.clear();
    }

    private void poll() {
        try {
            Long owned = commands.<Long>eval(RENEW, ScriptOutputType.INTEGER,
                new String[]{backend.namespace() + "epoch", node()}, backend.liveEpoch(), token)
                .toCompletableFuture().join();
            if (owned != 1) {
                backend.fence(new IllegalStateException("Redis network endpoint ownership was lost"));
                worker.shutdown();
                return;
            }
            var messages = commands.xrange(stream(), Range.create('(' + position, "+"), Limit.from(256))
                .toCompletableFuture().join();
            for (var entry : messages) {
                position = entry.getId();
                try {
                    MessageEnvelope message = codec.decode(Base64.getDecoder().decode(entry.getBody().get("payload")));
                    if (!message.isExpired(System.currentTimeMillis()) && accepts(message)) {
                        receivers.forEach(receiver -> receiver.accept(message));
                    }
                } catch (RuntimeException failure) {
                    logger.log(Level.WARNING, "Ignoring an invalid or failed Redis control message", failure);
                }
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Redis control-message connection is unavailable", failure);
        }
    }

    private boolean accepts(final MessageEnvelope message) {
        return switch (message.getTarget().getType()) {
            case PROXY -> proxy;
            case SERVER -> !proxy && serverId.equals(message.getTarget().getValue());
            case PLAYER -> !proxy && ownsPlayer.test(UUID.fromString(message.getTarget().getValue()));
            case ALL_SERVERS -> !proxy && !serverId.equals(message.getSourceServer());
        };
    }

    private String stream() {
        return backend.namespace() + "messages:" + backend.liveEpoch();
    }

    private String node() {
        // The current directory and transfer APIs have one proxy authority; backend endpoints scale independently.
        return backend.namespace() + "nodes:" + backend.liveEpoch() + ':' + (proxy ? "proxy" : "server:" + serverId);
    }
}
