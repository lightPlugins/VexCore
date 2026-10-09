package dev.vexsoft.core.paper.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.reward.PrivateRewardItemListener;
import dev.vexsoft.core.paper.service.reward.RewardItemService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/** Verifies that load-time services stay available without registering enabled-only runtime hooks. */
public final class GameplayModuleTest {

    @Test
    public void loadsGameplayBeforePluginEnableAndRegistersListenerOnlyWhenStarted() throws Exception {
        var pluginManager = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
            new Class<?>[] {PluginManager.class}, (proxy, method, args) -> {
                if (method.getName().equals("isPluginEnabled")) {
                    return false;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] {Server.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getPluginManager")) {
                    return pluginManager;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previousServer = serverField.get(null);
        serverField.set(null, server);

        try {
            var fixture = new Services();
            var manager = new ModuleManager(fixture.registry);
            manager.enable(new GameplayModule());

            assertTrue(fixture.registeredServices.contains(RewardItemService.class));
            assertTrue(fixture.listeners.isEmpty());

            fixture.pluginEnabled = true;
            manager.startAll();
            manager.startAll();

            assertEquals(List.of(PrivateRewardItemListener.class), fixture.listeners);
        } finally {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void rejectsStartingBeforeServicesHaveLoaded() {
        assertThrows(IllegalStateException.class, new GameplayModule()::start);
    }

    private static final class Services {

        private final List<Class<?>> registeredServices = new ArrayList<>();
        private final List<Class<?>> listeners = new ArrayList<>();
        private final Map<Class<?>, Object> instances = new HashMap<>();
        private boolean pluginEnabled;
        private final VexServiceRegistry registry = (VexServiceRegistry) Proxy.newProxyInstance(
            VexServiceRegistry.class.getClassLoader(), new Class<?>[] {VexServiceRegistry.class},
            (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "scoped" -> proxy;
                    case "register" -> {
                        registeredServices.add((Class<?>) args[0]);
                        yield null;
                    }
                    case "registerQueuedServices", "unregisterOwnedServices" -> null;
                    case "require" -> instances.computeIfAbsent((Class<?>) args[0], this::service);
                    case "find" -> Optional.empty();
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });

        private Object service(final Class<?> type) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                if (type == ListenerService.class && method.getName().equals("register")) {
                    if (!pluginEnabled) {
                        throw new IllegalStateException("Cannot register a listener while the plugin is disabled");
                    }
                    assertSame(registry, args[1]);
                    listeners.add((Class<?>) args[0]);
                }
                return null;
            });
        }
    }
}
