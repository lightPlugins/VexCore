package dev.vexsoft.core.paper.mob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.mob.flight.FlightBounds;
import dev.vexsoft.core.api.mob.flight.FlightState;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobScope;
import dev.vexsoft.core.paper.mob.service.runtime.VexMobRuntimeCoordinatorService;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/** Runs production ownership and AI handoff without allowing feature-level velocity or teleport writes. */
final class MobFlightOrderTest {

    @Test
    void repeatedOrdersActivateGoalLessCarriersOnceAndReleaseWithoutStoppingMotion() throws Exception {
        try (var fixture = new Fixture()) {
            var target = new Location(fixture.world, 3, 65, 0);
            for (int tick = 0; tick < 5; tick++) {
                assertEquals(FlightState.MOVING, fixture.service.flyTo(fixture.owner, fixture.handle, target, 8));
            }
            assertEquals(1, fixture.activations);
            assertEquals(5, fixture.orders);
            assertEquals(65, target.getY(), "Native adapters cannot mutate the caller's target");
            assertTrue(NmsMobGoalControl.isPaused(fixture.carrier));
            fixture.service.releaseFlight(fixture.owner, fixture.handle);
            assertEquals(1, fixture.releases);
            assertFalse(NmsMobGoalControl.isPaused(fixture.carrier));
        }
    }

    @Test
    void foreignRetiredAndInvalidOrdersCannotReachTheNativeController() throws Exception {
        try (var fixture = new Fixture()) {
            var target = new Location(fixture.world, 3, 65, 0);
            ServiceOwner foreign = () -> "Other";
            assertThrows(IllegalArgumentException.class, () ->
                fixture.service.flyTo(foreign, fixture.handle, target, 8));
            assertThrows(IllegalArgumentException.class, () ->
                fixture.service.flyTo(fixture.owner, fixture.handle, target, Double.NaN));
            assertThrows(IllegalArgumentException.class, () ->
                fixture.service.flyTo(fixture.owner, fixture.handle, target, 8,
                    new FlightBounds(0, 0, 1, 64, 66)));
            fixture.entries.clear();
            assertThrows(IllegalStateException.class, () ->
                fixture.service.flyTo(fixture.owner, fixture.handle, target, 8));
            assertThrows(IllegalStateException.class, () ->
                fixture.service.releaseFlight(fixture.owner, fixture.handle));
            assertEquals(0, fixture.orders);
            assertEquals(0, fixture.activations);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final ServiceOwner owner = () -> "VexIsles";
        private final MobHandle handle = new MobHandle(UUID.randomUUID(), MobKey.of("vexisles", "bee"));
        private final UUID entityId = UUID.randomUUID();
        private final World world = proxy(World.class, (instance, method, arguments) -> {
            if (method.getName().equals("equals")) {
                return instance == arguments[0];
            }
            throw new AssertionError(method.getName());
        });
        private final Mob carrier;
        private final VexMobRuntimeCoordinatorService service;
        private final Map<UUID, Object> entries;
        private boolean ai;
        private int orders;
        private int activations;
        private int releases;

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            var pathfinder = proxy(com.destroystokyo.paper.entity.Pathfinder.class,
                (instance, method, arguments) -> {
                    assertEquals("stopPathfinding", method.getName());
                    return null;
                });
            carrier = proxy(Mob.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> entityId;
                case "getWorld" -> world;
                case "hasAI" -> ai;
                case "setTarget" -> null;
                case "getPathfinder" -> pathfinder;
                default -> throw new AssertionError("Unexpected carrier mutation: " + method.getName());
            });
            Plugin plugin = proxy(Plugin.class, (instance, method, arguments) -> null);
            PluginManager manager = proxy(PluginManager.class, (instance, method, arguments) -> plugin);
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (instance, method, arguments) -> manager));
            var nms = proxy(NmsMobAdapterService.class, (instance, method, arguments) -> switch (method.getName()) {
                case "flyTo" -> {
                    orders++;
                    ((Location) arguments[1]).setY(999);
                    yield FlightState.MOVING;
                }
                case "activateGoals" -> {
                    activations++;
                    ai = true;
                    yield null;
                }
                case "releaseFlight" -> {
                    releases++;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            service = new VexMobRuntimeCoordinatorService(proxy(VexServiceRegistry.class,
                (instance, method, arguments) -> arguments[0] == NmsMobAdapterService.class ? nms : null));
            Class<?> type = Class.forName(VexMobRuntimeCoordinatorService.class.getName() + "$RuntimeMob");
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var definition = MobDefinition.builder(handle.definitionKey(), EntityType.BEE).build();
            Object runtime = constructor.newInstance("vexisles", handle, definition, MobScope.global(),
                carrier, 20, 1, null);
            Field field = VexMobRuntimeCoordinatorService.class.getDeclaredField("mobs");
            field.setAccessible(true);
            entries = (Map<UUID, Object>) field.get(service);
            entries.put(handle.instanceId(), runtime);
        }

        @Override
        public void close() throws Exception {
            NmsMobGoalControl.setPaused(carrier, false);
            serverField.set(null, previousServer);
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
