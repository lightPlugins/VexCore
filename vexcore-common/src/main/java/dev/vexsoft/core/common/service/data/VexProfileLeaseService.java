package dev.vexsoft.core.common.service.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Maintains database-clock leases with a stable fence separate from heartbeat revisions. */
@Dependencies(ProfileStorageService.class)
public final class VexProfileLeaseService implements ProfileLeaseService, AutoCloseable {

    private static final Duration LEASE_DURATION = Duration.ofSeconds(45);
    private final ProfileStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<PlayerProfileContext, HeldLease> leases = new ConcurrentHashMap<>();
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("VexCore-profile-leases").factory());
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Function<PlayerProfileContext, CompletableFuture<Void>> retirementHandler;
    private volatile CompletableFuture<Void> shutdownDrain = CompletableFuture.completedFuture(null);

    public VexProfileLeaseService(final VexServiceRegistry services) {
        storage = Objects.requireNonNull(services, "services").require(ProfileStorageService.class);
        renewals.scheduleWithFixedDelay(this::renewLeases, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public synchronized AutoCloseable onRetirement(
        final Function<PlayerProfileContext, CompletableFuture<Void>> handler
    ) {
        if (retirementHandler != null) {
            throw new IllegalStateException("A platform retirement handler is already registered");
        }
        retirementHandler = Objects.requireNonNull(handler, "handler");
        return () -> {
            synchronized (this) {
                if (retirementHandler == handler) {
                    retirementHandler = null;
                }
            }
        };
    }

    @Override
    public CompletableFuture<List<ProfileDocumentChange>> retire(final PlayerDataAddress address) {
        if (address.domain() == null || address.playerId() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A personal profile is required"));
        }
        CompletableFuture<List<ProfileDocumentChange>> result = new CompletableFuture<>();
        awaitRetirement(address, System.nanoTime() + Duration.ofSeconds(50).toNanos(), result);
        return result;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfileContext>> acquire(
        final ProfileDomain domain,
        final UUID profileId,
        final UUID playerId,
        final UUID sessionId,
        final long generation,
        final long membershipEpoch
    ) {
        if (closed.get() || leases.size() >= 1024) {
            return CompletableFuture.failedFuture(new IllegalStateException("Profile session capacity unavailable"));
        }
        PlayerDataAddress address = PlayerDataAddress.personal(domain, profileId, playerId);
        ProfileDocumentKey fenceKey = fenceKey(address);
        ProfileDocumentKey heartbeatKey = heartbeatKey(address);
        long started = System.nanoTime();
        return storage.databaseTime().thenCompose(now -> storage.load(List.of(fenceKey, heartbeatKey))
            .thenCompose(documents -> {
                LeaseFence previous = decode(documents.get(fenceKey), LeaseFence.class,
                    new LeaseFence(null, 0, 0, false));
                Heartbeat heartbeat = decode(documents.get(heartbeatKey), Heartbeat.class, new Heartbeat(0));
                if (previous.active() && heartbeat.expiresAt() > now) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                PlayerProfileContext context = new PlayerProfileContext(
                    domain, profileId, playerId, sessionId, generation, membershipEpoch,
                    Math.addExact(previous.fence(), 1));
                long fenceRevision = revision(documents.get(fenceKey));
                long heartbeatRevision = revision(documents.get(heartbeatKey));
                return storage.commit(List.of(
                    new ProfileDocumentChange(fenceKey, fenceRevision,
                        encode(new LeaseFence(sessionId, context.fence(), membershipEpoch, true))),
                    new ProfileDocumentChange(heartbeatKey, heartbeatRevision,
                        encode(new Heartbeat(now + LEASE_DURATION.toMillis())))
                )).thenCompose(committed -> {
                    if (!committed) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    HeldLease held = new HeldLease(context, fenceRevision + 1, heartbeatRevision + 1, started,
                        now + LEASE_DURATION.toMillis());
                    leases.put(context, held);
                    if (closed.get()) {
                        return release(context).thenApply(ignored -> Optional.empty());
                    }
                    return CompletableFuture.completedFuture(Optional.of(context));
                });
            }));
    }

    @Override
    public ProfileDocumentChange writeGuard(final PlayerProfileContext context) {
        HeldLease held = leases.get(Objects.requireNonNull(context, "context"));
        if (held == null || !held.writable()) {
            throw new IllegalStateException("Profile session lease expired or retired");
        }
        return ProfileDocumentChange.check(fenceKey(context.address()), held.fenceRevision, held.expiresAt);
    }

    @Override
    public boolean isWritable(final PlayerProfileContext context) {
        HeldLease held = leases.get(Objects.requireNonNull(context, "context"));
        return held != null && held.writable();
    }

    @Override
    public CompletableFuture<Void> release(final PlayerProfileContext context) {
        HeldLease held = leases.get(Objects.requireNonNull(context, "context"));
        if (held == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (held) {
            if (held.releasing && !held.pending.isCompletedExceptionally()) {
                return held.pending;
            }
            held.releasing = true;
            held.retired = true;
            CompletableFuture<Void> preceding = held.pending;
            held.pending = preceding.handle((ignored, failure) -> null).thenCompose(ignored -> storage.commit(List.of(
                new ProfileDocumentChange(fenceKey(context.address()), held.fenceRevision,
                    encode(new LeaseFence(context.sessionId(), context.fence(), context.membershipEpoch(), false))),
                new ProfileDocumentChange(heartbeatKey(context.address()), held.heartbeatRevision.get(),
                    encode(new Heartbeat(0)))
            ))).thenAccept(committed -> {
                leases.remove(context, held);
                if (!committed) {
                    throw new IllegalStateException("Profile ownership changed before release");
                }
            });
            return held.pending;
        }
    }

    @Override
    public void drainBeforeClose(final CompletableFuture<Void> drain) {
        shutdownDrain = Objects.requireNonNull(drain, "drain");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        renewals.shutdownNow();
        shutdownDrain.handle((ignored, failure) -> null).thenRun(() -> List.copyOf(leases.keySet()).forEach(this::release));
    }

    private void renewLeases() {
        for (HeldLease held : leases.values()) {
            synchronized (held) {
                if (!held.writable() || !held.pending.isDone()) {
                    continue;
                }
                long started = System.nanoTime();
                held.pending = storage.databaseTime().thenCompose(now -> storage.commit(List.of(
                    ProfileDocumentChange.check(fenceKey(held.context.address()), held.fenceRevision),
                    new ProfileDocumentChange(heartbeatKey(held.context.address()), held.heartbeatRevision.get(),
                        encode(new Heartbeat(now + LEASE_DURATION.toMillis())))
                )).thenAccept(committed -> {
                    if (!committed) {
                        held.retired = true;
                        return;
                    }
                    synchronized (held) {
                        held.heartbeatRevision.incrementAndGet();
                    }
                    held.deadline = started + LEASE_DURATION.toNanos();
                    held.expiresAt = now + LEASE_DURATION.toMillis();
                })).thenCompose(ignored -> checkRetirement(held));
            }
        }
    }

    private CompletableFuture<Void> checkRetirement(final HeldLease held) {
        Function<PlayerProfileContext, CompletableFuture<Void>> handler = retirementHandler;
        if (handler == null || held.retirementRequested || held.retired) {
            return CompletableFuture.completedFuture(null);
        }
        return storage.load(List.of(retirementKey(held.context.address()))).thenAccept(documents -> {
            Retirement request = decode(documents.get(retirementKey(held.context.address())), Retirement.class,
                new Retirement(0));
            if (request.fence() == held.context.fence()) {
                held.retirementRequested = true;
                // Do not chain the drain into the heartbeat: release must be able to await this heartbeat.
                handler.apply(held.context).exceptionally(failure -> {
                    held.retirementRequested = false;
                    return null;
                });
            }
        });
    }

    private void awaitRetirement(
        final PlayerDataAddress address,
        final long deadline,
        final CompletableFuture<List<ProfileDocumentChange>> result
    ) {
        if (result.isDone()) {
            return;
        }
        if (closed.get() || System.nanoTime() >= deadline) {
            result.completeExceptionally(new IllegalStateException("Profile retirement timed out"));
            return;
        }
        ProfileDocumentKey fence = fenceKey(address);
        ProfileDocumentKey heartbeat = heartbeatKey(address);
        ProfileDocumentKey request = retirementKey(address);
        storage.databaseTime().thenCompose(now -> storage.load(List.of(fence, heartbeat, request)).thenCompose(stored -> {
            LeaseFence owner = decode(stored.get(fence), LeaseFence.class, new LeaseFence(null, 0, 0, false));
            Heartbeat alive = decode(stored.get(heartbeat), Heartbeat.class, new Heartbeat(0));
            if (!owner.active() || alive.expiresAt() <= now) {
                result.complete(List.of(ProfileDocumentChange.check(fence, revision(stored.get(fence))),
                    ProfileDocumentChange.check(heartbeat, revision(stored.get(heartbeat)))));
                return CompletableFuture.completedFuture(null);
            }
            Retirement previous = decode(stored.get(request), Retirement.class, new Retirement(0));
            if (previous.fence() == owner.fence()) {
                return CompletableFuture.completedFuture(null);
            }
            return storage.commit(List.of(ProfileDocumentChange.check(fence, revision(stored.get(fence))),
                new ProfileDocumentChange(request, revision(stored.get(request)), encode(new Retirement(owner.fence())))))
                .thenApply(ignored -> null);
        })).whenComplete((ignored, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
            } else if (!result.isDone()) {
                renewals.schedule(() -> awaitRetirement(address, deadline, result), 1, TimeUnit.SECONDS);
            }
        });
    }

    private static ProfileDocumentKey retirementKey(final PlayerDataAddress address) {
        return new ProfileDocumentKey(address, "vexcore", "session_retirement");
    }

    private record Retirement(long fence) {
    }

    private String encode(final Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize profile ownership", failure);
        }
    }

    private <T> T decode(final StoredProfileDocument document, final Class<T> type, final T fallback) {
        if (document == null) {
            return fallback;
        }
        try {
            return mapper.readValue(document.value(), type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid stored profile ownership", failure);
        }
    }

    private static long revision(final StoredProfileDocument document) {
        return document == null ? 0 : document.revision();
    }

    private static ProfileDocumentKey fenceKey(final PlayerDataAddress address) {
        return new ProfileDocumentKey(address, "vexcore", "session_lease");
    }

    private static ProfileDocumentKey heartbeatKey(final PlayerDataAddress address) {
        return new ProfileDocumentKey(address, "vexcore", "session_heartbeat");
    }

    private record LeaseFence(UUID sessionId, long fence, long membershipEpoch, boolean active) {
    }

    private record Heartbeat(long expiresAt) {
    }

    private static final class HeldLease {

        private final PlayerProfileContext context;
        private final long fenceRevision;
        private final AtomicLong heartbeatRevision;
        private volatile long deadline;
        private volatile long expiresAt;
        private volatile boolean retired;
        private volatile boolean retirementRequested;
        private boolean releasing;
        private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);

        private HeldLease(
            final PlayerProfileContext context,
            final long fenceRevision,
            final long heartbeatRevision,
            final long started,
            final long expiresAt
        ) {
            this.context = context;
            this.fenceRevision = fenceRevision;
            this.heartbeatRevision = new AtomicLong(heartbeatRevision);
            deadline = started + LEASE_DURATION.toNanos();
            this.expiresAt = expiresAt;
        }

        private boolean writable() {
            return !retired && System.nanoTime() < deadline;
        }
    }
}
