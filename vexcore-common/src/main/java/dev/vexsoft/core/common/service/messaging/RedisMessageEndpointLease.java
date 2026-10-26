package dev.vexsoft.core.common.service.messaging;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Waits for an expired process lease without ever replacing a still-owned Redis messaging endpoint. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class RedisMessageEndpointLease implements AutoCloseable {

    private static final String RELEASE =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0";
    private final RedisAsyncCommands<String, String> commands;
    private final String key;
    private final String token;
    private final ScheduledExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> acquired = new CompletableFuture<>();

    CompletableFuture<Void> acquire(final Duration timeout) {
        attempt(System.nanoTime() + timeout.toNanos());
        return acquired.copy();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        acquired.completeExceptionally(new IllegalStateException("Redis messaging endpoint closed"));
        release().orTimeout(5, TimeUnit.SECONDS).join();
    }

    private void attempt(final long deadline) {
        if (closed.get() || acquired.isDone()) {
            return;
        }
        try {
            commands.set(key, token, SetArgs.Builder.nx().px(15000)).toCompletableFuture()
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        acquired.completeExceptionally(failure);
                    } else if ("OK".equals(result)) {
                        if (closed.get()) {
                            // A SET issued before close may complete after its first compare-and-delete.
                            release();
                        } else {
                            acquired.complete(null);
                        }
                    } else if (System.nanoTime() >= deadline) {
                        acquired.completeExceptionally(new IllegalStateException("Duplicate Redis network endpoint: " + key));
                    } else if (!closed.get()) {
                        retry(deadline);
                    }
                });
        } catch (RuntimeException failure) {
            acquired.completeExceptionally(failure);
        }
    }

    private void retry(final long deadline) {
        try {
            worker.schedule(() -> attempt(deadline), 250, TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            acquired.completeExceptionally(failure);
        }
    }

    private CompletableFuture<Long> release() {
        return commands.<Long>eval(RELEASE, ScriptOutputType.INTEGER, new String[]{key}, token).toCompletableFuture();
    }
}
