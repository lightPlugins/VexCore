package dev.vexsoft.core.paper.service.packets.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.service.VirtualPassengerOverlayService;
import dev.vexsoft.core.paper.packets.service.VirtualBlockService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

/** Prevents old Netty channels from recreating viewer state during disconnect and reconnect. */
public final class VexPacketConnectionLifecycleTest {

    @Test
    void oldConnectionCannotRewritePacketsAfterQuitOrIntoNewSession() {
        UUID viewerId = UUID.randomUUID();
        List<PacketDuplexHandler> handlers = new ArrayList<>();
        List<Object> rewritten = new ArrayList<>();
        List<UUID> cleared = new ArrayList<>();
        World world = proxy(World.class, (instance, method, arguments) -> NamespacedKey.minecraft("overworld"));
        Player viewer = proxy(Player.class, (instance, method, arguments) ->
            method.getName().equals("getWorld") ? world : viewerId);
        VexServiceRegistry registry = proxy(VexServiceRegistry.class, (instance, method, arguments) -> {
            Class<?> dependency = (Class<?>) arguments[0];
            return proxy(dependency, (target, call, parameters) -> {
                if (dependency == VirtualBlockService.class) {
                    return null;
                }
                if (dependency == PacketConnectionAdapterService.class) {
                    if (call.getName().equals("inject")) {
                        handlers.add((PacketDuplexHandler) parameters[1]);
                    }
                    return null;
                }
                if (dependency == VirtualPassengerOverlayService.class) {
                    if (call.getName().equals("removeViewer")) {
                        cleared.add((UUID) parameters[0]);
                        return null;
                    }
                    rewritten.add(parameters[1]);
                }
                return parameters[1];
            });
        });
        VexPacketConnectionService service = new VexPacketConnectionService(registry);
        Object packet = new Object();
        service.inject(viewer);
        service.inject(viewer);
        assertEquals(1, handlers.size());
        PacketDuplexHandler old = handlers.getFirst();
        assertSame(packet, old.write(viewerId, packet));
        assertEquals(1, rewritten.size());
        service.uninject(viewer);
        assertSame(packet, old.write(viewerId, packet));
        assertEquals(1, rewritten.size());
        service.inject(viewer);
        assertEquals(2, handlers.size());
        assertSame(packet, old.write(viewerId, packet));
        assertEquals(1, rewritten.size());
        assertSame(packet, handlers.getLast().write(viewerId, packet));
        assertEquals(2, rewritten.size());
        assertEquals(List.of(viewerId, viewerId, viewerId), cleared);
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
