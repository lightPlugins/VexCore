package dev.vexsoft.core.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.network.BackendShutdown;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.common.data.PlayerDataStore;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import net.kyori.adventure.text.Component;

@Timeout(10)
public final class PlayerShutdownDrainTest {

    @Test
    public void redirectsOnlyAStoppingServerWithConfirmedPersistence() {
        var kicks = new ArrayList<Component>();
        var player = stub(Player.class, (instance, method, arguments) -> {
            assertEquals("kick", method.getName());
            assertEquals(PlayerKickEvent.Cause.PLUGIN, arguments[1]);
            kicks.add((Component) arguments[0]);
            return null;
        });
        VexCorePlugin.disconnectStoppedPlayers(false, true, List.of(player));
        VexCorePlugin.disconnectStoppedPlayers(true, false, List.of(player));
        assertTrue(kicks.isEmpty());
        VexCorePlugin.disconnectStoppedPlayers(true, true, List.of(player));
        assertEquals(1, kicks.size());
        assertTrue(BackendShutdown.matches(kicks.getFirst()));
        assertFalse(BackendShutdown.matches(Component.text("Server closed")));
    }

    @Test
    public void keepsPluginAvailableUntilRetirementSaveAndCheckpointFinish() throws Exception {
        try (var fixture = new Fixture()) {
            var shutdown = fixture.shutdown(failure -> {
                throw new AssertionError("Unexpected recovery", failure);
            });
            fixture.registered.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("retire"), fixture.operations);
            assertFalse(shutdown.isDone());

            fixture.retirement.complete(null);
            fixture.saving.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("retire", "save"), fixture.operations);
            assertFalse(shutdown.isDone());

            fixture.save.complete(null);
            fixture.checkpointing.get(5, TimeUnit.SECONDS);
            assertFalse(shutdown.isDone());
            assertFalse(fixture.unloaded.get());

            fixture.checkpoint.complete(null);
            shutdown.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("retire", "save", "checkpoint", "unload"), fixture.operations);
            assertTrue(fixture.unloaded.get());
            assertTrue(fixture.persisted.get());
        }
    }

    @Test
    public void waitsForRecoveryExportWhenCheckpointFails() throws Exception {
        try (var fixture = new Fixture()) {
            var failure = new IllegalStateException("Checkpoint unavailable");
            var recovering = new CompletableFuture<Throwable>();
            var shutdown = fixture.shutdown(exception -> {
                fixture.operations.add("recovery");
                recovering.complete(exception);
                fixture.recovery.join();
                assertFalse(fixture.unloaded.get());
            });
            fixture.retirement.complete(null);
            fixture.save.complete(null);
            fixture.checkpointing.get(5, TimeUnit.SECONDS);
            fixture.checkpoint.completeExceptionally(failure);
            assertSame(failure, recovering.get(5, TimeUnit.SECONDS).getCause());
            assertFalse(shutdown.isDone());
            assertFalse(fixture.unloaded.get());

            fixture.recovery.complete(null);
            shutdown.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("retire", "save", "checkpoint", "recovery", "unload"), fixture.operations);
            assertFalse(fixture.persisted.get());
        }
    }

    @Test
    public void exportsRecoveryAndSkipsCheckpointWhenSaveFails() throws Exception {
        try (var fixture = new Fixture()) {
            var failure = new IllegalStateException("Save unavailable");
            var recovering = new CompletableFuture<Throwable>();
            var shutdown = fixture.shutdown(recovering::complete);
            fixture.retirement.complete(null);
            fixture.save.completeExceptionally(failure);
            shutdown.get(5, TimeUnit.SECONDS);
            assertSame(failure, recovering.get(5, TimeUnit.SECONDS).getCause());
            assertEquals(List.of("retire", "save", "unload"), fixture.operations);
        }
    }

    @Test
    public void exportsRecoveryWhenRetirementThrowsBeforeReturningAFuture() throws Exception {
        try (var fixture = new Fixture()) {
            var failure = new IllegalStateException("Entity unavailable");
            fixture.retirementFailure = failure;
            var recovering = new CompletableFuture<Throwable>();
            fixture.shutdown(recovering::complete).get(5, TimeUnit.SECONDS);
            assertSame(failure, recovering.get(5, TimeUnit.SECONDS).getCause());
            assertEquals(List.of("retire", "unload"), fixture.operations);
        }
    }

    private static <T> T stub(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture implements AutoCloseable {

        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final CompletableFuture<Void> retirement = new CompletableFuture<>();
        private final CompletableFuture<Void> save = new CompletableFuture<>();
        private final CompletableFuture<Void> checkpoint = new CompletableFuture<>();
        private final CompletableFuture<Void> recovery = new CompletableFuture<>();
        private final CompletableFuture<Void> registered = new CompletableFuture<>();
        private final CompletableFuture<Void> saving = new CompletableFuture<>();
        private final CompletableFuture<Void> checkpointing = new CompletableFuture<>();
        private final List<String> operations = new CopyOnWriteArrayList<>();
        private final AtomicBoolean unloaded = new AtomicBoolean();
        private final AtomicBoolean persisted = new AtomicBoolean();
        private RuntimeException retirementFailure;
        private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");
        private final PlayerDataStore backend = stub(PlayerDataStore.class, (instance, method, arguments) -> {
            if (method.getName().equals("checkpoint")) {
                assertFalse(unloaded.get());
                operations.add("checkpoint");
                checkpointing.complete(null);
                return checkpoint;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        private final PlayerDataStoreService storage = stub(PlayerDataStoreService.class,
            (instance, method, arguments) -> switch (method.getName()) {
                case "getStore" -> backend;
                case "drainBeforeClose" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        private final ProfileLeaseService leases = stub(ProfileLeaseService.class, (instance, method, arguments) -> {
            if (method.getName().equals("drainBeforeClose")) {
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        private final PlayerDataCoordinatorService players = stub(PlayerDataCoordinatorService.class,
            (instance, method, arguments) -> switch (method.getName()) {
                case "getLoadedPlayers" -> List.of(player);
                case "saveAll" -> {
                    assertFalse(unloaded.get());
                    operations.add("save");
                    saving.complete(null);
                    yield save;
                }
                case "drainBeforeClose" -> {
                    registered.complete(null);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        private final ProfileSessionService profiles = stub(ProfileSessionService.class,
            (instance, method, arguments) -> {
                if (method.getName().equals("retire")) {
                    assertSame(player, arguments[0]);
                    operations.add("retire");
                    if (retirementFailure != null) {
                        throw retirementFailure;
                    }
                    return retirement;
                }
                throw new UnsupportedOperationException(method.getName());
            });

        private CompletableFuture<Void> shutdown(final Consumer<Throwable> recover) {
            return CompletableFuture.runAsync(() -> {
                persisted.set(VexCorePlugin.drainPlayerData(players, profiles, storage, leases, recover));
                unloaded.set(true);
                operations.add("unload");
            }, executor);
        }

        @Override
        public void close() {
            retirement.complete(null);
            save.complete(null);
            checkpoint.complete(null);
            recovery.complete(null);
            executor.close();
        }
    }
}
