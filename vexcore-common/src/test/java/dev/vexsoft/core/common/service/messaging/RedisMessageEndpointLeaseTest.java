package dev.vexsoft.core.common.service.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies restart contention and token-guarded cleanup without an external Redis installation. */
final class RedisMessageEndpointLeaseTest {

    @Test
    void waitsForAnExpiredPreviousProcessLeaseAndThenAcquires() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.owner.set("previous-process");
            fixture.expireAfterFirstRejection = true;

            fixture.lease.acquire(Duration.ofSeconds(3)).get(4, TimeUnit.SECONDS);

            assertEquals("new-process", fixture.owner.get());
            assertEquals(2, fixture.attempts.get());
        }
    }

    @Test
    void aLiveDuplicateIsRejectedAndItsLeaseIsNeverDeleted() {
        try (var fixture = new Fixture()) {
            fixture.owner.set("other-live-process");
            var failure = assertThrows(CompletionException.class,
                () -> fixture.lease.acquire(Duration.ZERO).join());
            assertTrue(failure.getCause().getMessage().contains("Duplicate Redis network endpoint"));
            fixture.lease.close();
            assertEquals("other-live-process", fixture.owner.get());
        }
    }

    @Test
    void closeWaitsForConfirmedReleaseBeforeTheConnectionMayBeClosed() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.lease.acquire(Duration.ZERO).join();
            var deletion = new CompletableFuture<Long>();
            fixture.nextRelease.set(deletion);
            var closing = CompletableFuture.runAsync(fixture.lease::close);
            fixture.releaseIssued.get(3, TimeUnit.SECONDS);

            assertFalse(closing.isDone());
            deletion.complete(1L);
            closing.get(3, TimeUnit.SECONDS);
            assertNull(fixture.owner.get());
        }
    }

    @Test
    void anAcquireCompletingAfterCloseReleasesItsOwnLateLease() {
        try (var fixture = new Fixture()) {
            var delayed = new CompletableFuture<String>();
            fixture.nextSet.set(delayed);
            var ready = fixture.lease.acquire(Duration.ofSeconds(3));
            fixture.lease.close();
            fixture.owner.set("new-process");
            delayed.complete("OK");

            assertThrows(CompletionException.class, ready::join);
            assertNull(fixture.owner.get());
        }
    }

    @Test
    void closingAnOldOwnerCannotRemoveAReplacementLease() {
        try (var fixture = new Fixture()) {
            fixture.lease.acquire(Duration.ZERO).join();
            fixture.owner.set("replacement-process");
            fixture.lease.close();
            assertEquals("replacement-process", fixture.owner.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> redisFuture(final CompletableFuture<T> future) {
        return (RedisFuture<T>) Proxy.newProxyInstance(RedisFuture.class.getClassLoader(),
            new Class<?>[]{RedisFuture.class}, (instance, method, arguments) -> {
                assertEquals("toCompletableFuture", method.getName());
                return future;
            });
    }

    private static final class Fixture implements AutoCloseable {

        private final AtomicReference<String> owner = new AtomicReference<>();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicReference<CompletableFuture<String>> nextSet = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<Long>> nextRelease = new AtomicReference<>();
        private final CompletableFuture<Void> releaseIssued = new CompletableFuture<>();
        private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        private final RedisMessageEndpointLease lease;
        private boolean expireAfterFirstRejection;

        @SuppressWarnings("unchecked")
        private Fixture() {
            var commands = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(
                RedisAsyncCommands.class.getClassLoader(), new Class<?>[]{RedisAsyncCommands.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "set" -> {
                        attempts.incrementAndGet();
                        var delayed = nextSet.getAndSet(null);
                        if (delayed != null) {
                            yield redisFuture(delayed);
                        }
                        boolean acquired = owner.compareAndSet(null, (String) arguments[1]);
                        if (!acquired && expireAfterFirstRejection && attempts.get() == 1) {
                            owner.set(null);
                        }
                        yield redisFuture(CompletableFuture.completedFuture(acquired ? "OK" : null));
                    }
                    case "eval" -> {
                        String token = ((String[]) arguments[3])[0];
                        long deleted = owner.compareAndSet(token, null) ? 1L : 0L;
                        var delayed = nextRelease.getAndSet(null);
                        releaseIssued.complete(null);
                        yield redisFuture(delayed == null ? CompletableFuture.completedFuture(deleted) : delayed);
                    }
                    default -> throw new AssertionError(method.getName());
                });
            lease = new RedisMessageEndpointLease(commands, "network:endpoint", "new-process", worker);
        }

        @Override
        public void close() {
            try {
                lease.close();
            } finally {
                worker.shutdownNow();
                worker.close();
            }
        }
    }
}
