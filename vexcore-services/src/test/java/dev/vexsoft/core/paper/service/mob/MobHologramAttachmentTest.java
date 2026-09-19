package dev.vexsoft.core.paper.service.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.FakePassengerMount;
import dev.vexsoft.core.paper.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Verifies packet ordering and independent pitch for initial and repeated mob attachments. */
public final class MobHologramAttachmentTest {

    @Test
    void resetsPitchAfterEveryMountWithoutChangingTheFishPosition() {
        Player viewer = proxy(Player.class, (instance, method, arguments) -> null);
        ServiceOwner owner = proxy(ServiceOwner.class, (instance, method, arguments) -> null);
        FakeDisplayHandle handle = new FakeDisplayHandle(owner, UUID.randomUUID(), 42,
            UUID.randomUUID(), FakeDisplayKind.TEXT);
        for (float pitch : new float[]{-90, -35, 0, 25, 90}) {
            Location fish = new Location(null, 12, 64, 30, 120, pitch);
            Entity carrier = proxy(Entity.class, (instance, method, arguments) -> fish);
            List<String> calls = new ArrayList<>();
            DisplayPassengerPacketService passengers = proxy(DisplayPassengerPacketService.class,
                (instance, method, arguments) -> {
                    assertEquals("addFakePassenger", method.getName());
                    FakePassengerMount mount = (FakePassengerMount) arguments[2];
                    assertSame(handle, mount.getHandle());
                    assertEquals(1.5F, mount.getOffsetY());
                    calls.add("mount");
                    return null;
                });
            TextDisplayPacketService displays = proxy(TextDisplayPacketService.class,
                (instance, method, arguments) -> {
                    assertEquals("teleport", method.getName());
                    assertEquals("mount", calls.getLast());
                    Location target = (Location) arguments[1];
                    assertEquals(0.0F, target.getPitch());
                    assertEquals(fish.getYaw(), target.getYaw());
                    assertEquals(fish.toVector(), target.toVector());
                    calls.add("upright");
                    return null;
                });

            MobHologramAttachment.attach(viewer, carrier, handle, 1.5F, passengers, displays);
            MobHologramAttachment.attach(viewer, carrier, handle, 1.5F, passengers, displays);

            assertEquals(List.of("mount", "upright", "mount", "upright"), calls);
            assertEquals(pitch, fish.getPitch());
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
