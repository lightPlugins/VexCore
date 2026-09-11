package dev.vexsoft.core.paper.service.packets;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.api.service.registry.*;
import dev.vexsoft.core.paper.packets.display.*;
import dev.vexsoft.core.paper.packets.dummy.*;
import dev.vexsoft.core.paper.packets.service.*;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

public final class VexPlayerDummyServiceTest {

    @Test
    public void lateSkinCannotResurrectRemovedDummy() {
        var fixture = new Fixture();
        var handle = fixture.spawn();

        fixture.service.remove(handle);
        fixture.skin.complete(Optional.of(new SkinTexture("texture", "signature")));
        fixture.flush();

        assertEquals(0, fixture.skinUpdates);
        assertFalse(fixture.service.isActive(handle));
        assertEquals(1, fixture.removes);
    }

    @Test
    public void alreadyQueuedSkinIsDiscardedAfterClose() {
        var fixture = new Fixture();

        fixture.spawn();
        fixture.skin.complete(Optional.of(new SkinTexture("texture", null)));

        assertEquals(1, fixture.queued.size());

        fixture.service.close();
        fixture.flush();

        assertEquals(0, fixture.skinUpdates);
    }

    @Test
    public void sendsEquipmentOnlyWhenChangedAndCleansUpOnWorldChange() {
        var fixture = new Fixture();
        var handle = fixture.spawn();

        fixture.service.armor(handle, new ItemStack[4]);
        fixture.service.armor(handle, new ItemStack[4]);

        assertEquals(1, fixture.armorUpdates);

        fixture.currentWorld = proxy(World.class, (proxyObject, invokedMethod, arguments) -> null);
        fixture.service.animate(handle);

        assertFalse(fixture.service.isActive(handle));
        assertEquals(1, fixture.removes);
    }

    @Test
    public void viewerAndOwnerIsolation() {
        var fixture = new Fixture();
        var handle = fixture.spawn();

        fixture.service.removeAll(UUID.randomUUID());

        assertTrue(fixture.service.isActive(handle));

        var foreign = new FakeDisplayHandle(() -> "other", fixture.id, 999, UUID.randomUUID(), FakeDisplayKind.DUMMY);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.remove(foreign));

        fixture.service.removeAll(fixture.id);

        assertFalse(fixture.service.isActive(handle));
    }

    @Test
    public void bobAndRotationArePeriodicAndCentered() {
        var motion = new BobRotation(.15, 4, 30);

        assertEquals(0, motion.height(0), 1e-9);
        assertEquals(.15, motion.height(1), 1e-9);
        assertEquals(-.15, motion.height(3), 1e-9);
        assertEquals(0, motion.height(4), 1e-9);
        assertEquals(180, motion.yaw(15), 1e-6);
        assertEquals(0, motion.yaw(30), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> new BobRotation(.15, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new BobRotation(Double.NaN, 4, 30));
    }

    private static final class Fixture {

        final UUID id = UUID.randomUUID();
        final ServiceOwner owner = () -> "test";
        World currentWorld = proxy(World.class, (proxyObject, invokedMethod, arguments) -> null);
        final List<Runnable> queued = new ArrayList<>();
        final CompletableFuture<Optional<SkinTexture>> skin = new CompletableFuture<>();
        int skinUpdates, armorUpdates, removes;
        final Player viewer = proxy(
            Player.class,
            (proxyObject, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> currentWorld;
                case "isOnline" -> true;
                default -> null;
            }
        );
        final DisplayPacketAdapterService adapter = proxy(
            DisplayPacketAdapterService.class,
            (proxyObject, invokedMethod, arguments) -> {
                switch (invokedMethod.getName()) {
                    case "allocateEntityId":
                        return 123;
                    case "spawnDummy":
                        assertSame(viewer, arguments[0]);
                        break;
                    case "skinDummy":
                        skinUpdates++;
                        break;
                    case "armorDummy":
                        armorUpdates++;
                        break;
                    case "removeDummy":
                        removes++;
                        break;
                }

                return null;
            }
        );
        final SkinService skins = player -> skin;
        final ScheduleService scheduler = proxy(
            ScheduleService.class,
            (proxyObject, invokedMethod, arguments) -> {
                queued.add((Runnable) arguments[1]);

                return Optional.empty();
            }
        );
        final VexServiceRegistry registry = proxy(
            VexServiceRegistry.class,
            (proxyObject, invokedMethod, arguments) -> {
                if (invokedMethod.getName().equals("getOwner")) {
                    return owner;
                }

                if (arguments[0] == DisplayPacketAdapterService.class) {
                    return adapter;
                }

                if (arguments[0] == SkinService.class) {
                    return skins;
                }

                if (arguments[0] == ScheduleService.class) {
                    return scheduler;
                }

                throw new AssertionError(arguments[0]);
            }
        );
        final VexPlayerDummyService service = new VexPlayerDummyService(registry);

        FakeDisplayHandle spawn() {
            return service.spawn(viewer, new Location(currentWorld, 1, 2, 3), new BobRotation(.15, 4, 30));
        }

        void flush() {
            List.copyOf(queued).forEach(Runnable::run);
            queued.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
