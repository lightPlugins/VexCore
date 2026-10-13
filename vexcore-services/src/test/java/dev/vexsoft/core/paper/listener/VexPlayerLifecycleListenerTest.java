package dev.vexsoft.core.paper.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.player.PlayerIdentityService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Ensures failed profile initialization is logged even after its player scheduler has retired. */
public final class VexPlayerLifecycleListenerTest {

    @Test
    void profileFailureIsLoggedBeforeSchedulingTheDisconnectedPlayer() throws Exception {
        var playerId = UUID.randomUUID();
        var owner = new VexPlayer(playerId, "Alex");
        var initialized = new CompletableFuture<Void>();
        List<LogRecord> logged = new ArrayList<>();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(final LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        var plugin = Proxy.newProxyInstance(Plugin.class.getClassLoader(),
            new Class<?>[]{Plugin.class, ServiceOwner.class}, (instance, method, arguments) -> switch (method.getName()) {
                case "getLogger" -> logger;
                case "getServiceOwnerName" -> "test";
                default -> throw new AssertionError(method.getName());
            });
        var scheduler = proxy(EntityScheduler.class, (instance, method, arguments) -> {
            assertEquals(1, logged.size(), "The failure must be visible before trying the retired scheduler");
            return null;
        });
        var platform = proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getScheduler" -> scheduler;
            default -> throw new AssertionError("Retired players must not receive callbacks: " + method.getName());
        });
        var players = proxy(PlayerDataCoordinatorService.class, (instance, method, arguments) -> switch (method.getName()) {
            case "load" -> CompletableFuture.completedFuture(owner);
            case "find" -> Optional.of(owner);
            case "remove" -> null;
            default -> throw new AssertionError(method.getName());
        });
        var dataStore = proxy(PlayerDataStoreService.class, (instance, method, arguments) -> Duration.ofSeconds(15));
        var identities = proxy(PlayerIdentityService.class, (instance, method, arguments) -> CompletableFuture.completedFuture(null));
        var profiles = proxy(ProfileSessionService.class, (instance, method, arguments) -> initialized);
        var services = proxy(VexServiceRegistry.class, (instance, method, arguments) -> {
            if (method.getName().equals("getOwner")) {
                return plugin;
            }
            if (arguments[0] == PlayerDataCoordinatorService.class) {
                return players;
            }
            if (arguments[0] == PlayerDataStoreService.class) {
                return dataStore;
            }
            if (arguments[0] == PlayerIdentityService.class) {
                return identities;
            }
            if (arguments[0] == ProfileSessionService.class) {
                return profiles;
            }
            return proxy((Class<?>) arguments[0], (dependency, call, values) -> {
                throw new AssertionError("A failed login must not publish gameplay signals");
            });
        });
        var listener = new VexPlayerLifecycleListener(services);
        var profile = proxy(PlayerProfile.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getId" -> playerId;
            case "getName" -> "Alex";
            default -> throw new AssertionError(method.getName());
        });
        var address = InetAddress.getLoopbackAddress();
        listener.onPlayerPreLogin(new AsyncPlayerPreLoginEvent("Alex", address, address, playerId,
            false, profile, "localhost", null));
        listener.onPlayerJoin(new PlayerJoinEvent(platform, Component.empty()));
        var failure = new IllegalStateException("Arrival world is unavailable");
        initialized.completeExceptionally(failure);

        assertEquals(1, logged.size());
        assertSame(failure, logged.getFirst().getThrown());
        assertEquals("Unable to restore player profiles " + playerId, logged.getFirst().getMessage());
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
