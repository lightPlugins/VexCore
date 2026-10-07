package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Exercises external return commands through the actual flight wrapper while combat goals are paused. */
final class V26_2FlightRetreatTest {

    @Test
    void pausedGoalsStillForwardRetreatCommandsWithoutReplacingThemWithIdleAscent() {
        for (double minimumHeight : new double[]{0.0D, 1.0D}) {
            try (var fixture = new Flight(minimumHeight)) {
                NmsMobGoalControl.setPaused(fixture.carrier, true);
                fixture.forbidIdlePositionRead = true;
                for (int tick = 0; tick < 3; tick++) {
                    // Native navigation submits a fresh waypoint each tick, including during retreat.
                    fixture.control.setWantedPosition(20, 64, 5, 1.2);
                    fixture.control.tick();
                }
                assertEquals(List.of(new Vec3(20, 64 + minimumHeight, 5), new Vec3(20, 64 + minimumHeight, 5),
                    new Vec3(20, 64 + minimumHeight, 5)), fixture.delegate.movements);
                assertEquals(1.2D, fixture.delegate.getSpeedModifier());
                assertEquals(3, fixture.delegate.ticks);
                assertEquals(0, fixture.delegate.waitRequests,
                    "A paused combat goal must not cancel the caller's return movement");
                assertTrue(NmsMobGoalControl.isPaused(fixture.carrier),
                    "The return path must not reactivate combat goals");
            }
        }
    }

    @Test
    void pausedIdleCarrierDoesNotStartAnAutonomousAscent() {
        try (var fixture = new Flight(1.0D)) {
            NmsMobGoalControl.setPaused(fixture.carrier, true);
            fixture.forbidIdlePositionRead = true;
            fixture.control.tick();
            assertTrue(fixture.delegate.movements.isEmpty());
            assertEquals(1, fixture.delegate.ticks);
        }
    }

    @Test
    void unpausedCarrierStillEnforcesItsMinimumFlightHeightWhileIdle() {
        try (var fixture = new Flight(1.0D)) {
            fixture.control.tick();
            assertEquals(List.of(new Vec3(0, 65, 0)), fixture.delegate.movements);
            assertEquals(1.0D, fixture.delegate.getSpeedModifier());
        }
    }

    private static final class Flight implements AutoCloseable {
        private final UUID id = UUID.randomUUID();
        private boolean forbidIdlePositionRead;
        private final World world = proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isChunkLoaded" -> true;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getBlockAt" -> {
                int x = (int) arguments[0];
                int y = (int) arguments[1];
                int z = (int) arguments[2];
                yield proxy(Block.class, (block, call, values) -> switch (call.getName()) {
                    case "isLiquid" -> false;
                    case "isPassable" -> y != 63;
                    case "getBoundingBox" -> new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    default -> throw new AssertionError(call.getName());
                });
            }
            default -> throw new AssertionError(method.getName());
        });
        private final LivingEntity carrier = proxy(LivingEntity.class, (instance, method, arguments) ->
            switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> {
                    assertTrue(!forbidIdlePositionRead, "Paused control must leave the external path in charge");
                    yield new Location(world, 0, 64, 0);
                }
                default -> throw new AssertionError(method.getName());
            });
        private final TrackingControl delegate = new TrackingControl();
        private final V26_2FlightMoveControl control;

        private Flight(final double minimumHeight) {
            control = new V26_2FlightMoveControl(null, carrier, delegate, minimumHeight);
        }

        @Override
        public void close() {
            NmsMobGoalControl.setPaused(carrier, false);
        }
    }

    /** Uses the native waypoint queue without constructing or spawning a server entity. */
    private static final class TrackingControl extends MoveControl<Mob> {
        private final List<Vec3> movements = new ArrayList<>();
        private int ticks;
        private int waitRequests;

        private TrackingControl() {
            super(null);
        }

        @Override
        public void tick() {
            ticks++;
            if (hasWanted()) {
                movements.add(new Vec3(getWantedX(), getWantedY(), getWantedZ()));
                super.setWait();
            }
        }

        @Override
        public void setWait() {
            waitRequests++;
            super.setWait();
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
