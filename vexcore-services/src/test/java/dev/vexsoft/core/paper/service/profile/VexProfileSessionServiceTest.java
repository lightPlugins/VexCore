package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.messaging.DeliveryResult;
import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.network.admission.AdmissionReference;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.world.WorldKey;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import dev.vexsoft.core.common.data.redis.IndeterminateCommitException;
import dev.vexsoft.core.common.messaging.teleport.TeleportAbort;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparation;
import dev.vexsoft.core.common.service.admission.VexAdmissionService;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.common.service.data.PlayerTransferStoreService;
import dev.vexsoft.core.common.service.data.PlayerTransferTicket;
import dev.vexsoft.core.common.service.data.VexPlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.VexPlayerTransferStoreService;
import dev.vexsoft.core.common.service.data.VexProfileLeaseService;
import dev.vexsoft.core.common.service.data.VexProfileStorageService;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.teleport.TransferDisplayService;
import dev.vexsoft.core.paper.service.teleport.VexTeleportCoordinatorService;
import dev.vexsoft.core.paper.service.world.WorldService;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Exercises the real session, coordinator and lease boundary with independent entity and database executors. */
class VexProfileSessionServiceTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("test:game");
    private static final DataContainerKey<String> VALUE =
        DataContainerKey.of("wallet", String.class, () -> "old", PlayerDataScope.profile(DOMAIN));
    private static final DataContainerKey<String> ACCOUNT = DataContainerKey.of("preferences", String.class, () -> "de");

    @Test
    void repeatedCachedMutationsCompleteInlineWithoutCaptureStorageOrReadinessChanges() throws Exception {
        try (var fixture = new Fixture()) {
            int loads = fixture.profileLoads;
            fixture.entity.submit(() -> {
                for (int action = 0; action < 100; action++) {
                    String value = "local-" + action;
                    var result = fixture.sessions.mutate(fixture.player, () -> {
                        fixture.player.replace(VALUE, value);
                        fixture.player.afterCommit(() -> fixture.effects++);
                        return value;
                    });
                    assertTrue(result.isDone());
                    assertEquals(value, result.join());
                    assertTrue(fixture.sessions.isReady(fixture.player));
                }
            }).get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.nativeCommits);
            assertEquals(loads, fixture.profileLoads);
            assertEquals(100, fixture.effects);
            assertTrue(fixture.player.getDirtyKeys().contains(VALUE));
        }
    }

    @Test
    void cachedMutationRejectsWrongThreadsAndUnrelatedAccountWrites() throws Exception {
        try (var fixture = new Fixture()) {
            assertThrows(ExecutionException.class, () -> fixture.sessions.mutate(fixture.player, () -> {
                throw new AssertionError("An off-thread caller must not run");
            }).get(5, TimeUnit.SECONDS));
            var result = fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                fixture.player.replace(ACCOUNT, "en");
                return null;
            })).get();
            assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertEquals("de", fixture.player.read(ACCOUNT, value -> value));
            assertEquals(0, fixture.nativeCommits);
        }
    }

    @Test
    void cachedMutationRejectsADurablyBusySessionWithoutWaiting() throws Exception {
        try (var fixture = new Fixture()) {
            var committed = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            var local = fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                throw new AssertionError("A durable operation must exclude local mutation");
            })).get();
            assertTrue(local.isDone());
            assertThrows(ExecutionException.class, () -> local.get(5, TimeUnit.SECONDS));
            fixture.commit.complete(null);
            committed.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void backgroundSaveFreezesWalletAndNativeStateTogetherWhileLaterMutationsRemainPlayable() throws Exception {
        for (boolean live : new boolean[]{false, true}) {
            try (var fixture = new Fixture(true, live)) {
                fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                    fixture.player.replace(VALUE, "before");
                    fixture.level = 11;
                    return null;
                }).join()).get();
                fixture.saveGate = new CompletableFuture<>();
                var saved = fixture.sessions.save(fixture.player);
                fixture.saving.get(5, TimeUnit.SECONDS);
                fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                    fixture.player.replace(VALUE, "after");
                    fixture.level = 22;
                    return null;
                }).join()).get();
                assertTrue(fixture.sessions.isReady(fixture.player));
                fixture.saveGate.complete(null);
                saved.get(5, TimeUnit.SECONDS);
                var context = fixture.player.findProfileContext(DOMAIN).orElseThrow();
                var before = fixture.coordinator.loadProfile(context).get(5, TimeUnit.SECONDS);
                assertEquals("before", before.values().get(VALUE));
                assertEquals(11, before.values().values().stream().filter(PaperProfileState.class::isInstance)
                    .map(PaperProfileState.class::cast).findFirst().orElseThrow().level());
                assertTrue(fixture.player.getDirtyKeys().contains(VALUE));
                fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
                var after = fixture.coordinator.loadProfile(context).get(5, TimeUnit.SECONDS);
                assertEquals("after", after.values().get(VALUE));
                assertEquals(22, after.values().values().stream().filter(PaperProfileState.class::isInstance)
                    .map(PaperProfileState.class::cast).findFirst().orElseThrow().level());
            }
        }
    }

    @Test
    void retirementSavesTheLastCachedMutationBeforeReleasingOwnership() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                fixture.player.replace(VALUE, "last-local");
                fixture.level = 33;
                return null;
            }).join()).get();
            var context = fixture.player.findProfileContext(DOMAIN).orElseThrow();
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
            var stored = fixture.coordinator.loadProfile(context).get(5, TimeUnit.SECONDS);
            assertEquals("last-local", stored.values().get(VALUE));
            assertEquals(33, stored.values().values().stream().filter(PaperProfileState.class::isInstance)
                .map(PaperProfileState.class::cast).findFirst().orElseThrow().level());
            assertFalse(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void delayedCommitGuardsMutationsButAllowsMovementUntilDurability() throws Exception {
        try (var fixture = new Fixture()) {
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            assertFalse(operation.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
            assertTrue(fixture.sessions.transact(fixture.player, () -> {
                throw new AssertionError("A busy session must reject before invoking the mutation");
            }).handle((value, failure) -> failure instanceof ProfileSessionBusyException).join());
            fixture.commit.complete(null);
            assertEquals("done", operation.get(5, TimeUnit.SECONDS));
            fixture.sessions.readiness(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
            assertEquals("mutated", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
            assertEquals(42, fixture.level);
        }
    }

    @Test
    void delayedBackgroundSaveLeavesInventoryAndMovementReady() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.saveGate = new CompletableFuture<>();
            fixture.entity.submit(() -> fixture.level = 11).get();
            var operation = fixture.sessions.save(fixture.player);
            fixture.saving.get(5, TimeUnit.SECONDS);
            assertFalse(operation.isDone());
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.saveGate.complete(null);
            operation.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void liveBackgroundSaveLeavesGameplayReadyAndCoalescesOverlappingSaves() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.saveGate = new CompletableFuture<>();
            fixture.entity.submit(() -> fixture.level = 11).get();
            var saving = fixture.sessions.save(fixture.player);
            fixture.saving.get(5, TimeUnit.SECONDS);
            assertFalse(saving.isDone());
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
            assertEquals(saving, fixture.sessions.save(fixture.player));
            assertEquals(1, fixture.nativeCommits);
            fixture.saveGate.complete(null);
            saving.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void queuedLiveAutosaveSettlesWhenTheEntityRetiresBeforeCapture() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var occupied = new CompletableFuture<Void>();
            var release = new CompletableFuture<Void>();
            fixture.entity.execute(() -> {
                occupied.complete(null);
                release.join();
            });
            occupied.get(5, TimeUnit.SECONDS);
            CompletableFuture<Void> saving;
            try {
                saving = fixture.sessions.save(fixture.player);
                fixture.entityRetired = true;
            } finally {
                release.complete(null);
            }
            saving.get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.nativeCommits);
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.leases.isWritable(fixture.context()));
            fixture.coordinator.saveAndRemove(fixture.player.getUniqueId()).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.coordinator.find(fixture.player.getUniqueId()).isEmpty());
        }
    }

    @Test
    void rejectedEntitySchedulingDoesNotLeaveLiveSaveOrRetirementPending() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.rejectScheduling = true;
            fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.nativeCommits);
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.leases.isWritable(fixture.context()));
        }
    }

    @Test
    void detachedPlayerIsNotReadyAndSavesDirtyDataWithoutSchedulingNativeCapture() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.player.unbindPlatformPlayer();
            fixture.rejectScheduling = true;
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.player.replace(VALUE, "earned");
            fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals("earned", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
        }
    }

    @Test
    void logoutCapturesFinalNativeStateWhileAnOlderAutosaveIsQueued() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var saving = new CompletableFuture<Void>();
            var retiring = new CompletableFuture<Void>();
            fixture.entity.submit(() -> {
                // Queue an async autosave while quit still owns the entity and can capture its final state.
                fixture.database.submit(() -> fixture.sessions.save(fixture.player)
                    .whenComplete((ignored, failure) -> complete(saving, failure))).get();
                fixture.level = 33;
                fixture.sessions.retire(fixture.player)
                    .whenComplete((ignored, failure) -> complete(retiring, failure));
                fixture.player.unbindPlatformPlayer();
                fixture.entityRetired = true;
                return null;
            }).get(5, TimeUnit.SECONDS);
            saving.get(5, TimeUnit.SECONDS);
            retiring.get(5, TimeUnit.SECONDS);
            assertEquals(1, fixture.nativeCommits);
            var stored = fixture.coordinator.loadProfile(fixture.context()).get(5, TimeUnit.SECONDS);
            var nativeState = stored.values().values().stream()
                .filter(PaperProfileState.class::isInstance)
                .map(PaperProfileState.class::cast)
                .findFirst().orElseThrow();
            assertEquals(33, nativeState.level());
            assertFalse(fixture.leases.isWritable(fixture.context()));
        }
    }

    @Test
    void rejectedNativeCaptureStillSavesDirtyDataInNonLiveMode() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.player.replace(VALUE, "earned");
            fixture.rejectScheduling = true;
            fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals("earned", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectedSchedulingFailsAnEconomicOperationBeforeItsMutation() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.rejectScheduling = true;
            assertTrue(fixture.change().handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            assertEquals("old", fixture.player.read(VALUE, value -> value));
            assertEquals(10, fixture.level);
            assertEquals(0, fixture.effects);
        }
    }

    @Test
    void confirmedAutosaveFailureReportsTheErrorAndLeavesCachedMutationsAvailable() throws Exception {
        for (boolean live : new boolean[]{false, true}) {
            try (var fixture = new Fixture(true, live)) {
                fixture.saveGate = new CompletableFuture<>();
                fixture.entity.submit(() -> fixture.level = 11).get();
                var saving = fixture.sessions.save(fixture.player);
                fixture.saving.get(5, TimeUnit.SECONDS);
                fixture.saveGate.completeExceptionally(new IllegalStateException("Database unavailable"));
                assertTrue(saving.handle((ignored, failure) -> failure != null).get(5, TimeUnit.SECONDS));
                assertTrue(fixture.sessions.isReady(fixture.player));
                assertEquals("still-playing", fixture.entity.submit(() -> fixture.sessions.mutate(fixture.player, () -> {
                    fixture.player.replace(VALUE, "still-playing");
                    return "still-playing";
                }).join()).get(5, TimeUnit.SECONDS));
                assertTrue(fixture.player.getDirtyKeys().contains(VALUE));
                fixture.saveGate = null;
                fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
                assertEquals("still-playing", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
            }
        }
    }

    @Test
    void logoutDuringTheCommitKeepsTheConfirmedPurchaseAndDrainsTheSession() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            var retiring = fixture.entity.submit(() -> {
                var retirement = fixture.sessions.retire(fixture.player);
                fixture.player.unbindPlatformPlayer();
                fixture.entityRetired = true;
                return retirement;
            }).get(5, TimeUnit.SECONDS);
            fixture.commit.complete(null);
            assertEquals("done", operation.get(5, TimeUnit.SECONDS));
            retiring.get(5, TimeUnit.SECONDS);
            assertEquals("mutated", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
            assertEquals(0, fixture.effects);
            assertFalse(fixture.leases.isWritable(fixture.context()));
        }
    }

    @Test
    void detachedOperationsFailWithoutLeavingReadinessOrRetirementPending() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.player.unbindPlatformPlayer();
            assertTrue(fixture.change().handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            assertTrue(fixture.sessions.activate(fixture.player,
                new ProfileSelection(DOMAIN, UUID.randomUUID(), 1, List.of()))
                .handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            fixture.sessions.readiness(fixture.player).get(5, TimeUnit.SECONDS);
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.leases.isWritable(fixture.context()));
        }
    }

    @Test
    void ambiguousLiveBackgroundSaveFencesGameplayAndPreservesTheLiveState() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.saveGate = new CompletableFuture<>();
            fixture.entity.submit(() -> fixture.level = 11).get();
            var saving = fixture.sessions.save(fixture.player);
            fixture.saving.get(5, TimeUnit.SECONDS);
            fixture.saveGate.completeExceptionally(new IndeterminateCommitException(new IllegalStateException("Timeout")));
            assertTrue(saving.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            assertEquals(11, fixture.entity.submit(() -> fixture.level).get());
            assertEquals(1, fixture.kicks);
        }
    }

    @Test
    void liveTransactionMutatesBeforeOneCommitWithoutLoadingOrSavingItsBaseline() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            assertEquals(42, fixture.entity.submit(() -> fixture.level).get());
            assertFalse(operation.isDone());
            assertEquals(1, fixture.nativeCommits);
            assertEquals(0, fixture.profileLoads);
            fixture.commit.complete(null);
            assertEquals("done", operation.get(5, TimeUnit.SECONDS));
            assertEquals(1, fixture.effects);
        }
    }

    @Test
    void failedLiveTransactionRetainsUnsavedGameplayBaselineForRetry() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.entity.submit(() -> {
                fixture.level = 20;
                fixture.player.replace(VALUE, "earned");
            }).get();
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            fixture.commit.completeExceptionally(new IllegalStateException("Write rejected"));
            assertTrue(operation.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            assertEquals("earned", fixture.player.read(VALUE, value -> value));
            assertEquals(20, fixture.entity.submit(() -> fixture.level).get());
            assertTrue(fixture.player.getDirtyKeys().contains(VALUE));
            fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals("earned", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
        }
    }

    @Test
    void liveTransactionWaitsForAnOutstandingBackgroundWrite() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.saveGate = new CompletableFuture<>();
            fixture.entity.submit(() -> fixture.level = 11).get();
            var saving = fixture.sessions.save(fixture.player);
            fixture.saving.get(5, TimeUnit.SECONDS);
            var operation = fixture.change();
            assertFalse(operation.isDone());
            assertEquals(11, fixture.entity.submit(() -> fixture.level).get());
            fixture.saveGate.complete(null);
            saving.get(5, TimeUnit.SECONDS);
            fixture.waiting.get(5, TimeUnit.SECONDS);
            assertEquals(2, fixture.nativeCommits);
            fixture.commit.complete(null);
            assertEquals("done", operation.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void transactionCanOvertakeAQueuedLiveCaptureWithoutDeadlocking() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var occupied = new CompletableFuture<Void>();
            var release = new CompletableFuture<Void>();
            fixture.entity.execute(() -> {
                occupied.complete(null);
                release.join();
            });
            occupied.get(5, TimeUnit.SECONDS);
            CompletableFuture<Void> saving;
            CompletableFuture<String> operation;
            try {
                saving = fixture.sessions.save(fixture.player);
                operation = fixture.change();
            } finally {
                release.complete(null);
            }
            saving.get(5, TimeUnit.SECONDS);
            fixture.waiting.get(5, TimeUnit.SECONDS);
            assertEquals(1, fixture.nativeCommits);
            fixture.commit.complete(null);
            assertEquals("done", operation.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void movementWaitsForInitialRestorationAndProfileSwitchTeleport() throws Exception {
        try (var fixture = new Fixture(false)) {
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));

            fixture.teleport = new CompletableFuture<>();
            fixture.teleporting = new CompletableFuture<>();
            var operation = fixture.sessions.activate(fixture.player,
                new ProfileSelection(DOMAIN, UUID.randomUUID(), 1, List.of()));
            fixture.teleporting.get(5, TimeUnit.SECONDS);
            assertFalse(operation.isDone());
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.teleport.complete(true);
            operation.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
        }
    }

    @Test
    void dependentEconomicOperationStartsOnlyAfterThePreviousSessionBecomesReady() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            var second = first.thenCompose(ignored -> {
                assertTrue(fixture.sessions.isReady(fixture.player));
                return fixture.sessions.transact(fixture.player, () -> {
                    fixture.player.replace(VALUE, "chained");
                    return ProfileMutation.of("second");
                });
            });
            fixture.commit.complete(null);
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertEquals("chained", fixture.coordinator.loadProfile(fixture.context()).join().values().get(VALUE));
        }
    }

    @Test
    void failedCommitRestoresNativeAndPersistentValuesWithoutRunningEffects() throws Exception {
        try (var fixture = new Fixture()) {
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.commit.completeExceptionally(new IllegalStateException("Database unavailable"));
            assertTrue(operation.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            fixture.sessions.readiness(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals("old", fixture.player.read(VALUE, value -> value));
            assertEquals(10, fixture.level);
            assertEquals(0, fixture.effects);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
        }
    }

    @Test
    void ambiguousCommitFencesTheSessionWithoutRestoringAnOlderInventory() throws Exception {
        try (var fixture = new Fixture()) {
            var operation = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            fixture.commit.completeExceptionally(new IndeterminateCommitException(new IllegalStateException("Timeout")));
            assertTrue(operation.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            fixture.entity.submit(() -> {
            }).get();
            assertEquals("mutated", fixture.player.read(VALUE, value -> value));
            assertEquals(42, fixture.level);
            assertEquals(0, fixture.effects);
            assertEquals(1, fixture.kicks);
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
        }
    }

    @Test
    void ambiguousBaselineSavePreventsTheEconomicMutationFromStarting() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.saveGate = new CompletableFuture<>();
            var operation = fixture.change();
            fixture.saving.get(5, TimeUnit.SECONDS);
            fixture.saveGate.completeExceptionally(new IndeterminateCommitException(new IllegalStateException("Timeout")));
            assertTrue(operation.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            fixture.entity.submit(() -> {
            }).get();
            assertEquals("old", fixture.player.read(VALUE, value -> value));
            assertEquals(10, fixture.level);
            assertEquals(1, fixture.kicks);
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.saveGate = null;
        }
    }

    @Test
    void aDrainedSessionCanReacquireTheSameProfileWithANewFence() throws Exception {
        try (var fixture = new Fixture()) {
            var original = fixture.context();
            fixture.sessions.retire(fixture.player).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            fixture.sessions.resume(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.context().fence() > original.fence());
            assertFalse(fixture.player.isCurrentContext(original));
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
        }
    }

    @Test
    void aSessionWithoutWritableOwnershipCannotMove() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.sessions.save(fixture.player).get(5, TimeUnit.SECONDS);
            fixture.leases.release(fixture.context()).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            // A disconnected expired session must not capture native state into an unwritable profile.
            fixture.player.unbindPlatformPlayer();
        }
    }

    @Test
    void nativeStateAndWalletRestoreIndependentlyAcrossTwoProfiles() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.commit.complete(null);
            fixture.change().get(5, TimeUnit.SECONDS);
            var first = fixture.context();
            var second = UUID.randomUUID();
            fixture.sessions.activate(fixture.player, new ProfileSelection(DOMAIN, second, 1, List.of()))
                .get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.level);
            assertEquals("old", fixture.player.read(VALUE, value -> value));
            fixture.sessions.transact(fixture.player, () -> {
                fixture.player.replace(VALUE, "second");
                fixture.level = 77;
                return ProfileMutation.<Void>of(null);
            }).get(5, TimeUnit.SECONDS);
            fixture.sessions.activate(fixture.player, new ProfileSelection(DOMAIN, first.profileId(), 1, List.of()))
                .get(5, TimeUnit.SECONDS);
            assertEquals(42, fixture.level);
            assertEquals("mutated", fixture.player.read(VALUE, value -> value));
            assertEquals("de", fixture.player.read(ACCOUNT, value -> value));
            assertFalse(fixture.player.isCurrentContext(first));
        }
    }

    @Test
    void rejectedSelectionRestoresThePreviousReadyContext() throws Exception {
        try (var fixture = new Fixture()) {
            var original = fixture.context();
            var conflict = ProfileDocumentChange.check(new ProfileDocumentKey(
                PlayerDataAddress.account(fixture.player.getUniqueId()), "test", "selection"), 1);
            var selection = new ProfileSelection(DOMAIN, UUID.randomUUID(), 1, List.of(conflict));
            assertTrue(fixture.sessions.activate(fixture.player, selection).handle((value, failure) -> failure != null)
                .get(5, TimeUnit.SECONDS));
            assertEquals(original, fixture.context());
            assertEquals(10, fixture.level);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.leases.isWritable(original));
            assertTrue(fixture.sessions.isMovementAllowed(fixture.player));
        }
    }

    @Test
    void postCommitRestoreFailureClosesTheSessionAndReleasesBothFences() throws Exception {
        try (var fixture = new Fixture()) {
            var previous = fixture.context();
            fixture.failRestore = true;
            assertTrue(fixture.sessions.activate(fixture.player,
                new ProfileSelection(DOMAIN, UUID.randomUUID(), 1, List.of()))
                .handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.sessions.isMovementAllowed(fixture.player));
            assertFalse(fixture.leases.isWritable(previous));
            assertFalse(fixture.leases.isWritable(fixture.context()));
            assertEquals(1, fixture.kicks);
        }
    }

    @Test
    void profileMutationCannotChargeAnUnrelatedAccountContainer() throws Exception {
        try (var fixture = new Fixture()) {
            var operation = fixture.sessions.transact(fixture.player, () -> {
                fixture.level = 50;
                fixture.player.replace(ACCOUNT, "charged");
                return ProfileMutation.<Void>of(null);
            });
            assertTrue(operation.handle((value, failure) -> failure != null).get(5, TimeUnit.SECONDS));
            fixture.sessions.readiness(fixture.player).get(5, TimeUnit.SECONDS);
            assertEquals("de", fixture.player.read(ACCOUNT, value -> value));
            assertEquals(10, fixture.level);
        }
    }

    @Test
    void proxyPreparationWaitsForTheConfirmedPurchaseAndPreservesItsFinalNativeState() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var teleports = new VexTeleportCoordinatorService(fixture.registry);
            var purchase = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            var request = new TeleportPreparation(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-1", "isles-2", null);
            var preparation = teleports.prepare(request);
            assertFalse(preparation.isDone());
            fixture.commit.complete(null);
            assertEquals("done", purchase.get(5, TimeUnit.SECONDS));
            assertTrue(preparation.get(5, TimeUnit.SECONDS));
            assertFalse(fixture.sessions.isReady(fixture.player));
            var ticket = fixture.transfers.claim(fixture.player.getUniqueId(), "isles-2", UUID.randomUUID())
                .get(5, TimeUnit.SECONDS).orElseThrow();
            var snapshot = fixture.coordinator.loadProfile(ticket.profiles().getFirst()).get(5, TimeUnit.SECONDS);
            assertEquals("mutated", snapshot.values().get(VALUE));
            assertTrue(snapshot.values().values().stream().anyMatch(value ->
                value instanceof PaperProfileState nativeState && nativeState.level() == 42));
            teleports.abort(new TeleportAbort(request.requestId(), request.playerId())).get(5, TimeUnit.SECONDS);
            assertFalse(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void abortDuringAPurchaseDrainsBeforeResumingAndCannotOverwriteTheConfirmedPurchase() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            var teleports = new VexTeleportCoordinatorService(fixture.registry);
            var purchase = fixture.change();
            fixture.waiting.get(5, TimeUnit.SECONDS);
            var request = new TeleportPreparation(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-1", "isles-2", null);
            var preparation = teleports.prepare(request);
            var aborted = teleports.abort(new TeleportAbort(request.requestId(), request.playerId()));
            assertFalse(aborted.isDone());
            fixture.commit.complete(null);
            assertEquals("done", purchase.get(5, TimeUnit.SECONDS));
            aborted.get(5, TimeUnit.SECONDS);
            assertFalse(preparation.get(5, TimeUnit.SECONDS));
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertEquals("mutated", fixture.player.read(VALUE, value -> value));
            assertEquals(42, fixture.entity.submit(() -> fixture.level).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void expiredAdmissionIsRejectedBeforeAnyProfileOrNativeStateIsInstalled() throws Exception {
        try (var fixture = new Fixture(false)) {
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            var reference = new AdmissionReference(UUID.randomUUID(),
                fixture.player.getUniqueId(), "isles", destination.server(), UUID.randomUUID());
            var options = new TransferOptions(reference, null, false);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", destination, List.of(), System.currentTimeMillis() + 30_000,
                null, false, options)).get(5, TimeUnit.SECONDS);
            assertThrows(Exception.class, () -> fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS));
            assertFalse(fixture.teleporting.isDone());
            assertFalse(fixture.resolvingArrival.isDone());
            assertEquals(0, fixture.nativeCommits);
            assertEquals(0, fixture.spawns);
            assertTrue(fixture.player.profileContexts().isEmpty());
        }
    }

    @Test
    void explicitAccountOnlyArrivalAcceptsSavedGameContextsWithoutInstallingThemInTheLobby() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.participant.close();
            var context = new PlayerProfileContext(DOMAIN, fixture.profile, fixture.player.getUniqueId(),
                UUID.randomUUID(), 1, 1, 1);
            var options = new TransferOptions(null, null, true);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", null, List.of(context), System.currentTimeMillis() + 30_000,
                null, false, options)).get(5, TimeUnit.SECONDS);
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertTrue(fixture.player.profileContexts().isEmpty());
            assertEquals(0, fixture.nativeCommits);
            assertFalse(fixture.teleporting.isDone());
        }
    }

    @Test
    void ordinaryArrivalStillRejectsABackendWithoutTheRequiredGameParticipant() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.participant.close();
            var context = new PlayerProfileContext(DOMAIN, fixture.profile, fixture.player.getUniqueId(),
                UUID.randomUUID(), 1, 1, 1);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", null, List.of(context), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            assertThrows(Exception.class, () -> fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS));
            assertTrue(fixture.player.profileContexts().isEmpty());
            assertEquals(0, fixture.nativeCommits);
            assertFalse(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void anExplicitAccountOnlyArrivalCannotActivateAGameDomain() throws Exception {
        try (var fixture = new Fixture(false)) {
            var options = new TransferOptions(null, null, true);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", null, List.of(), System.currentTimeMillis() + 30_000,
                null, false, options)).get(5, TimeUnit.SECONDS);
            assertThrows(Exception.class, () -> fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS));
            assertFalse(fixture.teleporting.isDone());
            assertEquals(0, fixture.nativeCommits);
            assertEquals(0, fixture.spawns);
        }
    }

    @Test
    void deadLobbyArrivalSurvivesEntityTaskRetirementWithoutRunningRespawnInsideItsCallback() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.dead = true;
            fixture.retireDeadTasks = true;
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "lobby-1", "isles-1", destination, List.of(), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.dead);
            assertTrue(fixture.regionFallbacks > 0);
            assertEquals(1, fixture.respawns);
            assertEquals(0, fixture.kicks);
            assertTrue(fixture.teleporting.isDone());
        }
    }

    @Test
    void regionRecoveryDoesNotRestoreAPlayerWhoDisconnectedBeforeItsTaskRan() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.dead = true;
            fixture.retireDeadTasks = true;
            fixture.holdRegions = true;
            var initialized = fixture.sessions.initialize(fixture.player);
            fixture.regionWaiting.get(5, TimeUnit.SECONDS);
            fixture.entity.submit(() -> {
                fixture.connected = false;
                fixture.heldRegions.forEach(Runnable::run);
            }).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> initialized.get(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.respawns);
            assertTrue(fixture.player.profileContexts().isEmpty());
            assertFalse(fixture.teleporting.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void initialLoginRespawnsStaleNativeDeathAfterInstallingTheProfile() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.dead = true;
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.dead);
            assertEquals(1, fixture.respawns);
            assertEquals(0, fixture.kicks);
            assertTrue(fixture.teleporting.isDone());
        }
    }

    @Test
    void lobbyArrivalWithNoPriorProfileRecoversADeadNativePlayerAndKeepsItsDestination() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.dead = true;
            fixture.failSpawn = true;
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "lobby-1", "isles-1", destination, List.of(), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.dead);
            assertEquals(1, fixture.respawns);
            assertEquals(0, fixture.spawns);
            assertEquals(0, fixture.kicks);
            assertTrue(fixture.resolvingArrival.isDone());
            assertTrue(fixture.teleporting.isDone());
        }
    }

    @Test
    void switchingAnActiveDeadProfileRemainsRejectedWithoutRespawningOrReplacingIt() throws Exception {
        try (var fixture = new Fixture()) {
            var previous = fixture.context();
            int loads = fixture.profileLoads;
            int restores = fixture.restores;
            fixture.dead = true;
            var changed = fixture.sessions.activate(fixture.player,
                new ProfileSelection(DOMAIN, UUID.randomUUID(), 1, List.of()));
            assertThrows(ExecutionException.class, () -> changed.get(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.respawns);
            assertEquals(loads, fixture.profileLoads);
            assertTrue(fixture.player.isCurrentContext(previous));
            assertEquals(restores, fixture.restores);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertEquals(0, fixture.kicks);
            assertTrue(fixture.dead);
        }
    }

    @Test
    void unsuccessfulLoginRespawnDoesNotApplyNativeStateOrFinishTheTransferSuccessfully() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.dead = true;
            fixture.failRespawn = true;
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "lobby-1", "isles-1", null, List.of(), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            var initialized = fixture.sessions.initialize(fixture.player);
            assertThrows(ExecutionException.class, () -> initialized.get(5, TimeUnit.SECONDS));
            assertEquals(1, fixture.respawns);
            assertEquals(1, fixture.kicks);
            assertEquals(10, fixture.level);
            assertFalse(fixture.teleporting.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
            assertFalse(fixture.leases.isWritable(fixture.context()));
        }
    }

    @Test
    void firstGameArrivalFromLobbyWithoutProfileContextsBootstrapsAndRestores() throws Exception {
        try (var fixture = new Fixture(false)) {
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "lobby-1", "isles-1", destination, List.of(), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertEquals(fixture.profile, fixture.context().profileId());
            assertTrue(fixture.teleporting.isDone());
            assertTrue(fixture.logs.isEmpty());
        }
    }

    @Test
    void failedArrivalLogsItsRequestAndRestoreCauseAfterThePlayerWasKicked() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.failRestore = true;
            var request = UUID.randomUUID();
            fixture.transfers.prepare(new PlayerTransferTicket(request, fixture.player.getUniqueId(),
                "lobby-1", "isles-1", null, List.of(), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                () -> fixture.sessions.initialize(fixture.player).get(5, TimeUnit.SECONDS));
            assertEquals(1, fixture.kicks);
            assertEquals(1, fixture.logs.size());
            var logged = fixture.logs.getFirst();
            assertTrue(logged.getMessage().contains(request.toString()));
            assertTrue(logged.getMessage().contains(fixture.player.getUniqueId().toString()));
            assertTrue(logged.getMessage().contains("source=lobby-1, target=isles-1"));
            assertTrue(logged.getThrown().toString().contains("Game projections unavailable"));
        }
    }

    @Test
    void explicitArrivalWaitsForItsWorldResolverAndBypassesTheOrdinarySpawn() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.failSpawn = true;
            fixture.arrival = new CompletableFuture<>();
            var expected = new PlayerProfileContext(DOMAIN, fixture.profile, fixture.player.getUniqueId(),
                UUID.randomUUID(), 1, 1, 1);
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", destination, List.of(expected), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            var initialized = fixture.sessions.initialize(fixture.player);
            fixture.resolvingArrival.get(5, TimeUnit.SECONDS);
            assertFalse(initialized.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
            fixture.arrival.complete(new Location(null, 10, 80, 20));
            initialized.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
            assertEquals(0, fixture.spawns);
            assertTrue(fixture.teleporting.isDone());
        }
    }

    @Test
    void initialLoginWaitsAsynchronouslyForAnOlderLeaseInsteadOfFailingImmediately() throws Exception {
        try (var fixture = new Fixture(false)) {
            fixture.rejectFirstAcquire.set(true);
            var initialized = fixture.sessions.initialize(fixture.player);
            fixture.waitingForOwnership.get(5, TimeUnit.SECONDS);
            assertFalse(initialized.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
            initialized.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.sessions.isReady(fixture.player));
        }
    }

    @Test
    void changedMembershipCannotApplyTheInventoryOrWarpFromAnOlderHandover() throws Exception {
        try (var fixture = new Fixture(false)) {
            var oldMembership = new PlayerProfileContext(DOMAIN, fixture.profile, fixture.player.getUniqueId(),
                UUID.randomUUID(), 1, 2, 1);
            var destination = new ServerPosition(new ServerId("isles-1"), new WorldKey("test", "remote"),
                10, 80, 20, 0, 0);
            fixture.transfers.prepare(new PlayerTransferTicket(UUID.randomUUID(), fixture.player.getUniqueId(),
                "isles-2", "isles-1", destination, List.of(oldMembership), System.currentTimeMillis() + 30_000,
                null, false)).get(5, TimeUnit.SECONDS);
            var initialized = fixture.sessions.initialize(fixture.player);
            try {
                initialized.get(5, TimeUnit.SECONDS);
                throw new AssertionError("A changed membership must reject the transferred snapshot");
            } catch (ExecutionException expected) {
                assertFalse(fixture.sessions.isReady(fixture.player));
                assertTrue(fixture.player.findProfileContext(DOMAIN).isEmpty());
                assertFalse(fixture.resolvingArrival.isDone());
                assertEquals(0, fixture.nativeCommits);
            }
        }
    }

    private static void complete(final CompletableFuture<Void> result, final Throwable failure) {
        if (failure == null) {
            result.complete(null);
        } else {
            result.completeExceptionally(failure);
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final ExecutorService entity = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("profile-test-entity").factory());
        private final ExecutorService database = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("profile-test-database").factory());
        private final Map<Class<?>, Object> dependencies = new HashMap<>();
        private final Logger logger = Logger.getAnonymousLogger();
        private final List<LogRecord> logs = new CopyOnWriteArrayList<>();
        private final MemoryPlayerDataStore memory = new MemoryPlayerDataStore();
        private final CompletableFuture<Void> commit = new CompletableFuture<>();
        private final CompletableFuture<Void> waiting = new CompletableFuture<>();
        private final CompletableFuture<Void> saving = new CompletableFuture<>();
        private CompletableFuture<Void> saveGate;
        private CompletableFuture<Boolean> teleport = CompletableFuture.completedFuture(true);
        private CompletableFuture<Void> teleporting = new CompletableFuture<>();
        private final UUID profile = UUID.randomUUID();
        private final Field serverField;
        private final Object previousServer;
        private final VexProfileLeaseService leases;
        private final VexPlayerDataCoordinatorService coordinator;
        private final VexProfileSessionService sessions;
        private final VexPlayer player;
        private final VexServiceRegistry registry;
        private final PlayerTransferStoreService transfers;
        private final AutoCloseable participant;
        private CompletableFuture<Location> arrival = CompletableFuture.completedFuture(new Location(null, 0, 80, 0));
        private final CompletableFuture<Void> resolvingArrival = new CompletableFuture<>();
        private boolean failSpawn;
        private int spawns;
        private final AtomicBoolean rejectFirstAcquire = new AtomicBoolean();
        private final CompletableFuture<Void> waitingForOwnership = new CompletableFuture<>();
        private int level = 10;
        private int effects;
        private int kicks;
        private volatile boolean dead;
        private volatile boolean connected = true;
        private volatile boolean retireDeadTasks;
        private boolean retiringCallback;
        private volatile boolean holdRegions;
        private int regionFallbacks;
        private final CompletableFuture<Void> regionWaiting = new CompletableFuture<>();
        private final List<Runnable> heldRegions = new CopyOnWriteArrayList<>();
        private boolean failRespawn;
        private int respawns;
        private int restores;
        private boolean failRestore;
        private int nativeCommits;
        private int profileLoads;
        private volatile boolean entityRetired;
        private volatile boolean rejectScheduling;

        private Fixture() throws Exception {
            this(true);
        }

        private Fixture(final boolean initialize) throws Exception {
            this(initialize, false);
        }

        private Fixture(final boolean initialize, final boolean live) throws Exception {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(final LogRecord record) {
                    logs.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (object, method, arguments) ->
                method.getName().equals("isOwnedByCurrentRegion")
                    ? Thread.currentThread().getName().equals("profile-test-entity") : null));
            var plugin = Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class, ServiceOwner.class}, (object, method, arguments) -> switch (method.getName()) {
                    case "getServiceOwnerName" -> "test";
                    case "getLogger" -> logger;
                    default -> null;
                });
            registry = proxy(VexServiceRegistry.class, (object, method, arguments) -> switch (method.getName()) {
                case "getOwner" -> plugin;
                case "find" -> Optional.ofNullable(dependencies.get(arguments[0]));
                default -> dependencies.get(arguments[0]);
            });
            var backend = (PlayerDataStore) Proxy.newProxyInstance(PlayerDataStore.class.getClassLoader(),
                new Class<?>[]{PlayerDataStore.class, ProfileDocumentStore.class}, (object, method, arguments) -> {
                    if (method.getName().equals("isLive")) {
                        return live;
                    }
                    if (method.getReturnType() != CompletableFuture.class) {
                        return method.invoke(memory, arguments);
                    }
                    CompletableFuture<Void> permitted = CompletableFuture.completedFuture(null);
                    if (method.getName().equals("commitProfileDocuments")) {
                        var changes = (Collection<ProfileDocumentChange>) arguments[0];
                        if (changes.stream().anyMatch(change -> change.key().name().startsWith("native_state_"))) {
                            nativeCommits++;
                        }
                        if (saveGate != null && changes.stream()
                            .anyMatch(change -> change.key().name().startsWith("native_state_"))) {
                            saving.complete(null);
                            permitted = saveGate;
                        }
                        if (changes.stream().anyMatch(change -> change.key().name().equals("wallet")
                            && "\"mutated\"".equals(change.value()))) {
                            waiting.complete(null);
                            permitted = commit;
                        }
                    }
                    if (method.getName().equals("loadProfileDocuments")) {
                        profileLoads++;
                    }
                    return permitted.thenComposeAsync(ignored -> {
                        try {
                            return (CompletableFuture<Object>) method.invoke(memory, arguments);
                        } catch (ReflectiveOperationException failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    }, database);
                });
            dependencies.put(PlayerDataStoreService.class, proxy(PlayerDataStoreService.class,
                (object, method, arguments) -> method.getName().equals("getStore") ? backend
                    : method.getName().equals("getLoginKickMessage") ? "Reconnect"
                    : CompletableFuture.completedFuture(null)));
            dependencies.put(CacheService.class, new VexCacheService(registry));
            dependencies.put(ProfileStorageService.class, new VexProfileStorageService(registry));
            dependencies.put(TransferDisplayService.class,
                proxy(TransferDisplayService.class, (object, method, arguments) -> null));
            dependencies.put(AdmissionService.class,
                new VexAdmissionService(registry));
            transfers = new VexPlayerTransferStoreService(registry);
            dependencies.put(PlayerTransferStoreService.class, transfers);
            dependencies.put(ServerIdentityService.class, proxy(ServerIdentityService.class,
                (object, method, arguments) -> new ServerId("isles-1")));
            dependencies.put(WorldService.class, proxy(WorldService.class, (object, method, arguments) -> Optional.empty()));
            dependencies.put(ScheduleService.class, proxy(ScheduleService.class, (object, method, arguments) -> {
                assertEquals("runAt", method.getName());
                regionFallbacks++;
                if (holdRegions) {
                    heldRegions.add((Runnable) arguments[1]);
                    regionWaiting.complete(null);
                } else {
                    entity.execute((Runnable) arguments[1]);
                }
                return null;
            }));
            dependencies.put(MessagingService.class, proxy(MessagingService.class,
                (object, method, arguments) -> DeliveryResult.SENT));
            leases = new VexProfileLeaseService(registry);
            dependencies.put(ProfileLeaseService.class, proxy(ProfileLeaseService.class, (object, method, arguments) -> {
                if (method.getName().equals("acquire") && rejectFirstAcquire.compareAndSet(true, false)) {
                    waitingForOwnership.complete(null);
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                return method.invoke(leases, arguments);
            }));
            coordinator = new VexPlayerDataCoordinatorService(registry);
            dependencies.put(PlayerDataCoordinatorService.class, coordinator);
            dependencies.put(PlayerDataCoordinatorService.class, coordinator);
            coordinator.register((ServiceOwner) plugin, keys -> keys.register(VALUE));
            var nativeInventory = proxy(PlayerInventory.class, (object, method, arguments) -> switch (method.getName()) {
                case "getContents" -> new ItemStack[43];
                case "getSize" -> 43;
                case "getHeldItemSlot" -> 0;
                default -> null;
            });
            var ender = proxy(Inventory.class, (object, method, arguments) ->
                method.getName().equals("getContents") ? new ItemStack[27] : null);
            var scheduler = proxy(EntityScheduler.class, (object, method, arguments) -> {
                if (rejectScheduling) {
                    return null;
                }
                entity.execute(() -> {
                    if (entityRetired || retireDeadTasks && dead) {
                        retiringCallback = true;
                        try {
                            ((Runnable) arguments[2]).run();
                        } finally {
                            retiringCallback = false;
                        }
                    } else {
                        ((Consumer<Object>) arguments[1]).accept(null);
                    }
                });
                return proxy(ScheduledTask.class, (task, taskMethod, taskArguments) -> null);
            });
            var nativePlayer = proxy(Player.class, (object, method, arguments) -> {
                if (method.getName().equals("getScheduler")) {
                    return scheduler;
                }
                assertEquals("profile-test-entity", Thread.currentThread().getName());
                return switch (method.getName()) {
                    case "kick" -> {
                        kicks++;
                        yield null;
                    }
                    case "getInventory" -> nativeInventory;
                    case "getEnderChest" -> ender;
                    case "isConnected", "isOnline" -> connected;
                    case "addPotionEffects" -> true;
                    case "getLocation" -> new Location(null, 0, 80, 0);
                    case "isDead" -> dead;
                    case "spigot" -> new Player.Spigot() {
                        @Override
                        public void respawn() {
                            assertFalse(retiringCallback, "Respawn must never run inside the scheduler's retirement callback");
                            assertTrue(player.findProfileContext(DOMAIN).isPresent(),
                                "Game respawn listeners require the target's scoped data");
                            assertFalse(sessions.isReady(player));
                            respawns++;
                            if (!failRespawn) {
                                dead = false;
                            }
                        }
                    };
                    case "getLevel" -> level;
                    case "setLevel" -> {
                        assertFalse(dead, "Native state must be restored only after successful respawn");
                        level = (int) arguments[0];
                        yield null;
                    }
                    case "getActivePotionEffects" -> List.of();
                    case "getTotalExperience", "getFireTicks" -> 0;
                    case "getRemainingAir", "getMaximumAir" -> 300;
                    case "getExp", "getExhaustion" -> 0F;
                    case "getSaturation" -> 5F;
                    case "getFoodLevel" -> 20;
                    case "teleportAsync" -> {
                        teleporting.complete(null);
                        yield teleport;
                    }
                    default -> null;
                };
            });
            sessions = new VexProfileSessionService(registry);
            dependencies.put(ProfileSessionService.class, sessions);
            participant = sessions.register(new PaperProfileParticipant() {
                @Override
                public ProfileDomain domain() {
                    return DOMAIN;
                }

                @Override
                public CompletableFuture<ProfileSelection> bootstrap(final VexPlayer owner) {
                    return CompletableFuture.completedFuture(new ProfileSelection(DOMAIN, profile, 1, List.of()));
                }

                @Override
                public void prepare(final Player owner, final PlayerProfileContext previous) {
                }

                @Override
                public void restore(final Player owner, final PlayerProfileContext current) {
                    restores++;
                    if (failRestore) {
                        throw new IllegalStateException("Game projections unavailable");
                    }
                }

                @Override
                public Location spawn(final Player owner) {
                    spawns++;
                    if (failSpawn) {
                        throw new IllegalStateException("The ordinary spawn world is not loaded");
                    }
                    return new Location(null, 0, 80, 0);
                }

                @Override
                public CompletableFuture<Location> arrivalDestination(final Player owner, final ServerPosition destination) {
                    resolvingArrival.complete(null);
                    return arrival;
                }
            });
            player = coordinator.load(UUID.randomUUID(), "Alex").get(5, TimeUnit.SECONDS);
            player.install(ACCOUNT, "de");
            player.bindPlatformPlayer(nativePlayer);
            if (initialize) {
                sessions.initialize(player).get(5, TimeUnit.SECONDS);
            }
            entity.submit(() -> level = 10).get();
            nativeCommits = 0;
            profileLoads = 0;
        }

        private CompletableFuture<String> change() {
            return sessions.transact(player, () -> {
                player.replace(VALUE, "mutated");
                level = 42;
                player.afterCommit(() -> effects++);
                return ProfileMutation.of("done");
            });
        }

        private PlayerProfileContext context() {
            return player.findProfileContext(DOMAIN).orElseThrow();
        }

        @Override
        public void close() throws Exception {
            sessions.retire(player).get(5, TimeUnit.SECONDS);
            sessions.close();
            leases.close();
            coordinator.close();
            entity.close();
            database.close();
            serverField.set(null, previousServer);
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
