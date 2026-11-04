package dev.vexsoft.core.common.data.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.common.data.PlayerDataStore;
import io.lettuce.core.RedisURI;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Verifies checkpoint isolation before initialization, without opening a Redis connection. */
final class RedisCheckpointFutureTest {

    @Test
    void callerCompletionAndCancellationCannotCompleteTheSharedCheckpoint() {
        var ready = new CompletableFuture<Void>();
        var durable = (PlayerDataStore) Proxy.newProxyInstance(PlayerDataStore.class.getClassLoader(),
            new Class<?>[]{PlayerDataStore.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "readiness" -> ready;
                case "networkCheckpoints", "close" -> null;
                default -> throw new AssertionError(method.getName());
            });
        var settings = new RedisSettings(RedisURI.create("redis://127.0.0.1:1"), "vexcore:test:",
            "checkpoint-isolation", 16, 1000, 300, Duration.ofSeconds(1));

        try (var store = new RedisPlayerDataStore(durable, settings)) {
            var completedByCaller = store.checkpoint();
            var cancelledByCaller = store.checkpoint();
            completedByCaller.complete(null);
            assertFalse(cancelledByCaller.isDone());

            cancelledByCaller.cancel(false);
            var observer = store.checkpoint();
            assertFalse(observer.isDone());

            var failure = new IllegalStateException("Durable initialization failed");
            ready.completeExceptionally(failure);
            assertSame(failure, assertThrows(CompletionException.class, observer::join).getCause());
        }
    }
}
