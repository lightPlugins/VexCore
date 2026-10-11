package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.profile.PlayerDataAddress;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentChange;
import dev.vexsoft.core.api.player.profile.storage.ProfileDocumentKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.player.profile.ProfileStorageService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.data.MemoryPlayerDataStore;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.data.profile.ProfileDocumentStore;
import dev.vexsoft.core.common.data.redis.IndeterminateCommitException;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.common.service.data.VexPlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.VexProfileLeaseService;
import dev.vexsoft.core.common.service.data.VexProfileStorageService;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

/** Exercises the real session, coordinator and lease boundary with independent entity and database executors. */
class VexProfileSessionServiceTest {

    private static final ProfileDomain DOMAIN = new ProfileDomain("test:game");
    private static final DataContainerKey<String> VALUE =
        DataContainerKey.of("wallet", String.class, () -> "old", PlayerDataScope.profile(DOMAIN));
    private static final DataContainerKey<String> ACCOUNT = DataContainerKey.of("preferences", String.class, () -> "de");

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
    void delayedSaveAllowsMovementWhileMutationsRemainBlocked() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.saveGate = new CompletableFuture<>();
            fixture.entity.submit(() -> fixture.level = 11).get();
            var operation = fixture.sessions.save(fixture.player);
            fixture.saving.get(5, TimeUnit.SECONDS);
            assertFalse(operation.isDone());
            assertFalse(fixture.sessions.isReady(fixture.player));
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

    private static final class Fixture implements AutoCloseable {

        private final ExecutorService entity = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("profile-test-entity").factory());
        private final ExecutorService database = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("profile-test-database").factory());
        private final Map<Class<?>, Object> dependencies = new HashMap<>();
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
        private int level = 10;
        private int effects;
        private int kicks;
        private boolean failRestore;
        private int nativeCommits;
        private int profileLoads;

        private Fixture() throws Exception {
            this(true);
        }

        private Fixture(final boolean initialize) throws Exception {
            this(initialize, false);
        }

        private Fixture(final boolean initialize, final boolean live) throws Exception {
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (object, method, arguments) ->
                method.getName().equals("isOwnedByCurrentRegion")
                    ? Thread.currentThread().getName().equals("profile-test-entity") : null));
            var plugin = Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class, ServiceOwner.class}, (object, method, arguments) ->
                    method.getName().equals("getServiceOwnerName") ? "test" : null);
            var registry = proxy(VexServiceRegistry.class, (object, method, arguments) -> switch (method.getName()) {
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
            leases = new VexProfileLeaseService(registry);
            dependencies.put(ProfileLeaseService.class, leases);
            coordinator = new VexPlayerDataCoordinatorService(registry);
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
                entity.execute(() -> ((Consumer<Object>) arguments[1]).accept(null));
                return null;
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
                    case "isConnected", "isOnline", "addPotionEffects" -> true;
                    case "isDead" -> false;
                    case "getLevel" -> level;
                    case "setLevel" -> {
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
            sessions.register(new PaperProfileParticipant() {
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
                    if (failRestore) {
                        throw new IllegalStateException("Game projections unavailable");
                    }
                }

                @Override
                public Location spawn(final Player owner) {
                    return new Location(null, 0, 80, 0);
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
