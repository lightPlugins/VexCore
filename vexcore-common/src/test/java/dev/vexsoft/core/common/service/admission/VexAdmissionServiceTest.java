package dev.vexsoft.core.common.service.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.network.admission.AdmissionEndpoint;
import dev.vexsoft.core.api.network.admission.AdmissionPool;
import dev.vexsoft.core.api.network.admission.AdmissionReference;
import dev.vexsoft.core.api.network.admission.AdmissionStage;
import dev.vexsoft.core.api.network.admission.AdmissionStatus;
import dev.vexsoft.core.api.network.admission.BackendPresence;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.profile.storage.StoredProfileDocument;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.world.WorldKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exercises two independent lobby instances against atomic asynchronous storage and authoritative time. */
class VexAdmissionServiceTest {

    @Test
    void farmingMapPoolsShareBackendCapacityAndOnlySelectLoadedDestinationWorlds() {
        try (var storage = new Storage()) {
            var hub = new VexAdmissionService(storage);
            var farming = new VexAdmissionService(storage);
            hub.configure(farmingPool("mine"), 1).join();
            hub.configure(farmingPool("fields"), 1).join();
            hub.heartbeat(new BackendPresence(new ServerId("farming-1"), UUID.randomUUID(), Set.of(),
                Set.of("test:mine"), "test:game", true, 0)).join();
            hub.heartbeat(new BackendPresence(new ServerId("farming-2"), UUID.randomUUID(), Set.of(),
                Set.of("test:mine", "test:fields"), "test:game", true, 0)).join();

            UUID firstPlayer = UUID.randomUUID();
            var mine = hub.enqueue(UUID.randomUUID(), firstPlayer, "hub", "isles_ws_mine").join();
            assertEquals("farming-1", mine.destination().server().value());
            var fields = farming.enqueue(UUID.randomUUID(), UUID.randomUUID(), "hub", "isles_ws_fields").join();
            assertEquals("farming-2", fields.destination().server().value());
            var waitingFields = hub.enqueue(UUID.randomUUID(), UUID.randomUUID(), "hub", "isles_ws_fields").join();
            var waitingMine = farming.enqueue(UUID.randomUUID(), UUID.randomUUID(), "hub", "isles_ws_mine").join();
            assertEquals(AdmissionStage.WAITING, waitingFields.stage());
            assertEquals(AdmissionStage.WAITING, waitingMine.stage());

            assertTrue(hub.cancel(mine.requestId(), firstPlayer).join());
            var allocated = farming.pulse(List.of(waitingFields.requestId(), waitingMine.requestId())).join();
            assertEquals(AdmissionStage.WAITING, allocated.get(waitingFields.requestId()).stage());
            assertEquals(AdmissionStage.RESERVED, allocated.get(waitingMine.requestId()).stage());
            assertEquals("farming-1", allocated.get(waitingMine.requestId()).destination().server().value());
        }
    }

    @Test
    void twentyConcurrentPlayersBalanceAcrossThreeBackendsWithoutOverbooking() {
        try (var storage = new Storage()) {
            var first = new VexAdmissionService(storage);
            var second = new VexAdmissionService(storage);
            first.configure(pool(50, 30, "a", "b", "c"), 1).join();
            for (String server : List.of("a", "b", "c")) {
                first.heartbeat(presence(server, UUID.randomUUID(), Set.of())).join();
            }
            var requests = new ArrayList<CompletableFuture<AdmissionStatus>>();
            for (int index = 0; index < 20; index++) {
                requests.add((index % 2 == 0 ? first : second).enqueue(UUID.randomUUID(), UUID.randomUUID(), "lobby", "isles"));
            }
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
            var counts = requests.stream().map(CompletableFuture::join).peek(status ->
                assertEquals(AdmissionStage.RESERVED, status.stage())).collect(Collectors.groupingBy(
                    status -> status.destination().server().value(), Collectors.counting()));
            assertEquals(List.of(6L, 7L, 7L), counts.values().stream().sorted().toList());
            assertTrue(storage.conflicts.get() > 0);
        }
    }

    @Test
    void parallelLimitAndFifoHoldWhenAPlayerArrivesBeforeItsNextHeartbeat() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            var backend = UUID.randomUUID();
            service.configure(pool(2, 1, "a"), 1).join();
            service.heartbeat(presence("a", backend, Set.of())).join();
            UUID player = UUID.randomUUID();
            var first = service.enqueue(UUID.randomUUID(), player, "lobby", "isles").join();
            UUID waiting = UUID.randomUUID();
            var second = service.enqueue(waiting, UUID.randomUUID(), "lobby", "isles").join();
            UUID last = UUID.randomUUID();
            assertEquals(2, service.enqueue(last, UUID.randomUUID(), "lobby", "isles").join().position());
            assertEquals(AdmissionStage.WAITING, second.stage());
            var reference = reference(first, player);
            assertTrue(service.begin(reference).join());
            assertTrue(service.validate(reference).join());
            assertFalse(service.cancel(first.requestId(), player).join());
            service.finish(reference, true).join();
            var states = service.pulse(List.of(waiting, last)).join();
            assertEquals(AdmissionStage.RESERVED, states.get(waiting).stage());
            assertEquals(AdmissionStage.WAITING, states.get(last).stage());
            service.heartbeat(presence("a", backend, Set.of(player))).join();
            states = service.pulse(List.of(waiting, last)).join();
            assertEquals(AdmissionStage.RESERVED, states.get(waiting).stage());
            assertEquals(AdmissionStage.WAITING, states.get(last).stage());
        }
    }

    @Test
    void backendRestartChangesNonceAndRejectsTheOldCountdownEvenForTheSameDestination() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            service.configure(pool(50, 4, "a"), 1).join();
            service.heartbeat(presence("a", UUID.randomUUID(), Set.of())).join();
            UUID player = UUID.randomUUID();
            var old = service.enqueue(UUID.randomUUID(), player, "lobby", "isles").join();
            storage.now.addAndGet(11000);
            service.heartbeat(presence("a", UUID.randomUUID(), Set.of())).join();
            var current = service.pulse(List.of(old.requestId())).join().get(old.requestId());
            assertEquals(old.destination(), current.destination());
            assertFalse(old.reservationId().equals(current.reservationId()));
            assertFalse(service.begin(reference(old, player)).join());
            assertTrue(service.begin(reference(current, player)).join());
        }
    }

    @Test
    void staleOfflineWrongDomainAndUnloadedWorldBackendsAreExcluded() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            service.configure(pool(50, 4, "a", "b", "c"), 1).join();
            service.heartbeat(presence("a", UUID.randomUUID(), Set.of())).join();
            service.heartbeat(new BackendPresence(new ServerId("b"), UUID.randomUUID(), Set.of(), Set.of("test:spawn"),
                "another:game", true, 0)).join();
            service.heartbeat(new BackendPresence(new ServerId("c"), UUID.randomUUID(), Set.of(), Set.of(),
                "test:game", true, 0)).join();
            storage.now.addAndGet(11000);
            assertEquals(AdmissionStage.WAITING, service.enqueue(UUID.randomUUID(), UUID.randomUUID(), "lobby", "isles")
                .join().stage());
        }
    }

    @Test
    void expirationCancellationAndStalePolicyCannotReuseCapacityOrRevertSettings() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            service.configure(pool(1, 4, "a"), 2).join();
            service.configure(pool(50, 4, "a"), 1).join();
            UUID instance = UUID.randomUUID();
            service.heartbeat(presence("a", instance, Set.of())).join();
            UUID player = UUID.randomUUID();
            var first = service.enqueue(UUID.randomUUID(), player, "lobby", "isles").join();
            UUID request = UUID.randomUUID();
            assertEquals(AdmissionStage.WAITING, service.enqueue(request, UUID.randomUUID(), "lobby", "isles").join().stage());
            assertTrue(service.cancel(first.requestId(), player).join());
            assertFalse(service.begin(reference(first, player)).join());
            assertEquals(AdmissionStage.RESERVED, service.pulse(List.of(request)).join().get(request).stage());
            storage.now.addAndGet(31000);
            assertEquals(AdmissionStage.EXPIRED, service.pulse(List.of(request)).join().get(request).stage());
            assertThrows(Exception.class, () -> service.configure(pool(10, 4, "a"), 2).join());
        }
    }

    @Test
    void aHeartbeatThatExpiresDuringTheAtomicCommitCannotApproveAConnection() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            service.configure(pool(50, 4, "a"), 1).join();
            service.heartbeat(presence("a", UUID.randomUUID(), Set.of())).join();
            UUID player = UUID.randomUUID();
            var status = service.enqueue(UUID.randomUUID(), player, "lobby", "isles").join();
            storage.expireOnGuard = true;
            assertFalse(service.begin(reference(status, player)).join());
            assertEquals(AdmissionStage.WAITING, service.pulse(List.of(status.requestId())).join().get(status.requestId()).stage());
        }
    }

    @Test
    void duplicatePlayerAndRequestIdentityCannotCreateMultipleReservations() {
        try (var storage = new Storage()) {
            var service = new VexAdmissionService(storage);
            service.configure(pool(50, 4, "a"), 1).join();
            UUID player = UUID.randomUUID();
            UUID request = UUID.randomUUID();
            var first = service.enqueue(request, player, "lobby", "isles").join();
            assertEquals(first, service.enqueue(request, player, "lobby", "isles").join());
            assertThrows(Exception.class, () -> service.enqueue(UUID.randomUUID(), player, "other-lobby", "isles").join());
            assertThrows(Exception.class, () -> service.enqueue(request, UUID.randomUUID(), "lobby", "isles").join());
        }
    }

    private static AdmissionReference reference(final AdmissionStatus status, final UUID player) {
        return new AdmissionReference(status.requestId(), player, "isles", status.destination().server(), status.reservationId());
    }

    private static AdmissionPool pool(final int maximum, final int parallel, final String... servers) {
        var endpoints = Arrays.stream(servers).map(server -> new AdmissionEndpoint(
            new ServerPosition(new ServerId(server), WorldKey.parse("test:spawn"), 0, 80, 0, 0, 0), maximum, parallel)).toList();
        return new AdmissionPool("isles", "test:game", endpoints, 512, 30000, 10000);
    }

    private static AdmissionPool farmingPool(final String map) {
        var endpoints = List.of("farming-1", "farming-2").stream().map(server -> new AdmissionEndpoint(
            new ServerPosition(new ServerId(server), WorldKey.parse("test:" + map), 0, 80, 0, 0, 0), 1, 1)).toList();
        return new AdmissionPool("isles_ws_" + map, "test:game", endpoints, 512, 30000, 10000);
    }

    private static BackendPresence presence(final String server, final UUID instance, final Set<UUID> players) {
        return new BackendPresence(new ServerId(server), instance, players, Set.of("test:spawn"), "test:game", true, 0);
    }

    private static final class Storage implements ProfileStorageService, AutoCloseable {

        private final Map<ProfileDocumentKey, StoredProfileDocument> documents = new HashMap<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final AtomicLong now = new AtomicLong(100000);
        private final AtomicLong conflicts = new AtomicLong();
        private boolean expireOnGuard;

        @Override
        public CompletableFuture<Map<ProfileDocumentKey, StoredProfileDocument>> load(final Collection<ProfileDocumentKey> keys) {
            return CompletableFuture.supplyAsync(() -> {
                synchronized (documents) {
                    var result = new HashMap<ProfileDocumentKey, StoredProfileDocument>();
                    keys.forEach(key -> {
                        if (documents.containsKey(key)) {
                            result.put(key, documents.get(key));
                        }
                    });
                    return Map.copyOf(result);
                }
            }, executor);
        }

        @Override
        public CompletableFuture<Boolean> commit(final Collection<ProfileDocumentChange> changes) {
            return CompletableFuture.supplyAsync(() -> {
                synchronized (documents) {
                    if (expireOnGuard) {
                        changes.stream().filter(change -> change.notAfterEpochMillis() > 0).findFirst()
                            .ifPresent(change -> now.set(change.notAfterEpochMillis()));
                    }
                    for (var change : changes) {
                        var current = documents.get(change.key());
                        if ((current == null ? 0 : current.revision()) != change.expectedRevision()
                            || change.notAfterEpochMillis() > 0 && now.get() >= change.notAfterEpochMillis()) {
                            conflicts.incrementAndGet();
                            return false;
                        }
                    }
                    changes.forEach(change -> {
                        if (change.value() != null) {
                            documents.put(change.key(), new StoredProfileDocument(change.value(), change.expectedRevision() + 1));
                        }
                    });
                    return true;
                }
            }, executor);
        }

        @Override
        public CompletableFuture<Long> databaseTime() {
            return CompletableFuture.completedFuture(now.get());
        }

        @Override
        public void close() {
            executor.close();
        }
    }
}
