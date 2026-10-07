package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.InventoryView;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Verifies that dialog suspension preserves history and cannot resurrect closed or replaced views. */
final class VexInventorySuspensionTest {

    @Test
    void suspendResumeAndBackKeepTheOriginalViewInstances() throws Exception {
        try (var fixture = new Fixture()) {
            var first = new View("test:first");
            var second = new View("test:second");
            fixture.service.open(fixture.player, first);
            fixture.service.open(fixture.player, second);
            fixture.service.suspend(fixture.player);
            assertSame(second, fixture.service.getCurrentView(fixture.id).orElseThrow());
            assertEquals(1, fixture.service.getSession(fixture.id).getHistory().size());
            assertEquals(0, second.closes);
            assertEquals(2, fixture.opens);
            fixture.service.refresh(fixture.player);
            assertEquals(2, fixture.opens);
            fixture.service.resume(fixture.player, second);
            assertEquals(3, fixture.opens);
            fixture.service.back(fixture.player);
            assertSame(first, fixture.service.getCurrentView(fixture.id).orElseThrow());
            assertEquals(1, second.closes);
        }
    }

    @Test
    void replacingOrClosingTheSessionInvalidatesItsPendingReturn() throws Exception {
        try (var fixture = new Fixture()) {
            var first = new View("test:first");
            var replacement = new View("test:replacement");
            fixture.service.open(fixture.player, first);
            fixture.service.suspend(fixture.player);
            fixture.service.replace(fixture.player, replacement);
            fixture.service.resume(fixture.player, first);
            assertSame(replacement, fixture.service.getCurrentView(fixture.id).orElseThrow());
            assertEquals(2, fixture.opens);
            fixture.service.suspend(fixture.player);
            fixture.service.close(fixture.player);
            fixture.service.resume(fixture.player, replacement);
            assertTrue(fixture.service.getCurrentView(fixture.id).isEmpty());
            assertEquals(2, fixture.opens);
        }
    }

    @Test
    void foreignNativeInventoryAndDisconnectPreventReopening() throws Exception {
        try (var fixture = new Fixture()) {
            var view = new View("test:crafting");
            fixture.service.open(fixture.player, view);
            fixture.service.suspend(fixture.player);
            fixture.top = fixture.inventory(null, 27, InventoryType.CHEST);
            fixture.service.resume(fixture.player, view);
            assertTrue(fixture.service.getCurrentView(fixture.id).isEmpty());
            assertEquals(1, fixture.opens);
            fixture.service.open(fixture.player, view);
            fixture.service.suspend(fixture.player);
            fixture.service.handleQuit(fixture.player);
            fixture.service.resume(fixture.player, view);
            assertTrue(fixture.service.getCurrentView(fixture.id).isEmpty());
            assertEquals(2, fixture.opens);
        }
    }

    @Test
    void closingSuspendedSessionDoesNotCloseAForeignNativeInventory() throws Exception {
        try (var fixture = new Fixture()) {
            var view = new View("test:crafting");
            fixture.service.open(fixture.player, view);
            fixture.service.suspend(fixture.player);
            var foreign = fixture.inventory(null, 27, InventoryType.CHEST);
            fixture.top = foreign;
            fixture.service.close(fixture.player);
            assertSame(foreign, fixture.top);
            assertTrue(fixture.service.getCurrentView(fixture.id).isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final UUID id = UUID.randomUUID();
        private final Field serverField = Bukkit.class.getDeclaredField("server");
        private final Object previousServer;
        private final VexInventoryService service;
        private Inventory top = inventory(null, 5, InventoryType.CRAFTING);
        private int opens;
        private final org.bukkit.inventory.InventoryView nativeView = proxy(org.bukkit.inventory.InventoryView.class,
            (object, method, arguments) -> switch (method.getName()) {
                case "getTopInventory" -> top;
                case "title" -> Component.empty();
                default -> null;
            });
        private final Player player = proxy(Player.class, (object, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getOpenInventory" -> nativeView;
            case "openInventory" -> {
                closeNative();
                top = (Inventory) arguments[0];
                opens++;
                yield nativeView;
            }
            case "closeInventory" -> {
                closeNative();
                yield null;
            }
            default -> null;
        });

        private Fixture() throws Exception {
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (object, method, arguments) -> switch (method.getName()) {
                case "isOwnedByCurrentRegion" -> true;
                case "createInventory" -> inventory((InventoryHolder) arguments[0], (int) arguments[1], InventoryType.CHEST);
                default -> null;
            }));
            Object plugin = Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class, ServiceOwner.class},
                (object, method, arguments) -> null);
            ScheduleService schedules = proxy(ScheduleService.class, (object, method, arguments) -> null);
            var registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                method.getName().equals("getOwner") ? plugin : schedules);
            service = new VexInventoryService(registry);
        }

        private Inventory inventory(final InventoryHolder holder, final int size, final InventoryType type) {
            return proxy(Inventory.class, (object, method, arguments) -> switch (method.getName()) {
                case "getHolder" -> holder;
                case "getSize" -> size;
                case "getType" -> type;
                default -> null;
            });
        }

        private void closeNative() {
            if (top.getHolder() instanceof VexInventoryHolder holder) {
                service.handleClose(player, holder);
            }
            top = inventory(null, 5, InventoryType.CRAFTING);
        }

        @Override
        public void close() throws Exception {
            service.close();
            serverField.set(null, previousServer);
        }
    }

    private static final class View implements InventoryView {

        private final InventoryKey key;
        private int closes;

        private View(final String key) {
            this.key = InventoryKey.of(key);
        }

        @Override
        public InventoryKey getKey() {
            return key;
        }

        @Override
        public int getSize() {
            return 9;
        }

        @Override
        public Component getTitle(final InventoryContext context) {
            return Component.empty();
        }

        @Override
        public Map<Integer, InventoryElement> getElements(final InventoryContext context) {
            return Map.of();
        }

        @Override
        public void onClose(final InventoryContext context) {
            closes++;
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
