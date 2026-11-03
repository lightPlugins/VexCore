package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import dev.vexsoft.core.paper.nms.v26_2.VexNmsMobAdapterService;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Production follow construction must keep working after a model plugin decorates the native controls. */
final class V26_2ModeledFollowerTest {

    @Test
    void aModeledCompanionFollowsWalkingOwnersBeforeTheTeleportThreshold() throws Exception {
        try (var follower = new ModeledFollower()) {
            assertTrue(follower.goal.canUse());
            follower.goal.start();
            follower.movement.setX(0.2);
            for (int tick = 0; tick < 240; tick++) {
                follower.ownerPosition.add(follower.movement);
                follower.goal.tick();
                follower.flight.tick();
                assertTrue(follower.flight.position.distance(follower.ownerPosition) < 6,
                    "The production follow goal did not reach the decorated flight controller");
            }
            assertEquals(0, follower.flight.recoveries);
        }
    }

    @Test
    void sideSpawnStaysBesideAStationaryOwnerAndFollowsASingleBlockStepWithoutTeleporting() throws Exception {
        try (var follower = new ModeledFollower()) {
            follower.flight.position = follower.ownerPosition.clone().add(1.5 * Math.sqrt(3) / 2, 2, -0.75);
            Location spawn = follower.flight.position.clone();
            assertTrue(follower.goal.canUse());
            follower.goal.start();
            for (int tick = 0; tick < 80; tick++) {
                follower.goal.tick();
                follower.flight.tick();
            }
            assertEquals(spawn.getX(), follower.flight.position.getX(), 0.000001);
            assertEquals(spawn.getZ(), follower.flight.position.getZ(), 0.000001);
            follower.ownerPosition.add(0, 0, 1);
            follower.goal.tick();
            follower.flight.tick();
            assertTrue(follower.flight.position.getZ() > spawn.getZ(),
                "A single position update must start flight even when Bukkit reports zero owner velocity");
            for (int tick = 0; tick < 240; tick++) {
                follower.goal.tick();
                follower.flight.tick();
            }
            assertEquals(spawn.getX(), follower.flight.position.getX(), 0.000001);
            assertEquals(spawn.getZ() + 1, follower.flight.position.getZ(), 0.051);
            assertEquals(0, follower.flight.recoveries);
        }
    }

    @Test
    void aLowCeilingKeepsTheFreeSideHeightInsteadOfSendingThePetAboveTheRoof() throws Exception {
        try (var follower = new ModeledFollower()) {
            follower.flight.ceiling = 66;
            follower.flight.position = follower.ownerPosition.clone().add(1.5, 1, 0);
            assertTrue(follower.goal.canUse());
            follower.goal.start();
            for (int tick = 0; tick < 80; tick++) {
                follower.goal.tick();
                follower.flight.tick();
            }
            assertEquals(65, follower.flight.position.getY(), 0.051);
            assertEquals(0, follower.flight.recoveries);
        }
    }

    @Test
    void modeledRecoveryDoesNotReturnToTheSpawnPointOrRepeatInALoop() throws Exception {
        try (var follower = new ModeledFollower()) {
            follower.ownerPosition.add(40, 0, 0);
            assertTrue(follower.goal.canUse());
            follower.goal.start();
            // Establish an idle spawn anchor before the goal's distance recovery.
            follower.flight.tick();
            follower.goal.tick();
            assertEquals(1, follower.flight.recoveries);
            Location recovered = follower.flight.position.clone();
            follower.movement.setX(0.2);
            for (int tick = 0; tick < 240; tick++) {
                follower.ownerPosition.add(follower.movement);
                follower.goal.tick();
                follower.flight.tick();
                assertTrue(follower.flight.position.getX() >= recovered.getX() - 0.01,
                    "The model wrapper hid the motor reset and the pet returned toward its spawn");
                assertTrue(follower.flight.position.distance(follower.ownerPosition) < 6);
            }
            assertEquals(1, follower.flight.recoveries);
        }
    }

    @Test
    void explicitFlightAndReleaseUseTheMotorBehindTheModelWrapper() throws Exception {
        try (var flight = new FlightFixture(1)) {
            flight.wrapMoveControl();
            var carrier = flight.craftCarrier();
            var services = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
                new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> null);
            var adapter = new VexNmsMobAdapterService(services);
            var start = flight.position.clone();
            adapter.flyTo(carrier, start.clone().add(8, 0, 0), 8, null);
            for (int tick = 0; tick < 5; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.getX() > start.getX() + 1);
            adapter.releaseFlight(carrier);
            var released = flight.position.clone();
            for (int tick = 0; tick < 40; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.distance(released) < 0.6,
                "Releasing a wrapped motor must retire the external destination");
            assertSame(flight.control, V26_2FlightMoveControl.find(flight.nativeMob));
            V26_2FlightMoveControl.forget(flight.nativeMob);
            assertNull(V26_2FlightMoveControl.find(flight.nativeMob));
        }
    }

    @Test
    void standingTurnsKeepThePetReachableAndWalkingResumesALowerRearSideFormation() throws Exception {
        try (var follower = new ModeledFollower(1)) {
            follower.flight.position = follower.ownerPosition.clone().add(1.5, 1, 0);
            assertTrue(follower.goal.canUse());
            follower.goal.start();
            Location spawn = follower.flight.position.clone();
            for (float yaw : new float[]{90, 180, -90, 0}) {
                follower.ownerPosition.setYaw(yaw);
                for (int tick = 0; tick < 40; tick++) {
                    follower.goal.tick();
                    follower.flight.tick();
                }
                assertEquals(spawn.getX(), follower.flight.position.getX(), 0.000001);
                assertEquals(spawn.getZ(), follower.flight.position.getZ(), 0.000001);
            }
            // Backpedal while looking left; Bukkit velocity remains zero as on real position updates.
            follower.ownerPosition.setYaw(90);
            for (int tick = 0; tick < 10; tick++) {
                follower.ownerPosition.add(0.1, 0, 0);
                follower.goal.tick();
                follower.flight.tick();
            }
            for (int tick = 0; tick < 160; tick++) {
                follower.goal.tick();
                follower.flight.tick();
            }
            Vector relative = follower.flight.position.toVector().subtract(follower.ownerPosition.toVector());
            assertEquals(1, relative.getY(), 0.051);
            assertTrue(relative.dot(follower.ownerPosition.getDirection()) < -0.7,
                "Backpedaling must not put the companion in front of the player's view");
            assertTrue(Math.abs(relative.getZ()) > 1.2, "The companion must retain a lateral offset");
            Location settled = follower.flight.position.clone();
            follower.ownerPosition.setYaw(-90);
            for (int tick = 0; tick < 160; tick++) {
                follower.goal.tick();
                follower.flight.tick();
            }
            assertEquals(settled.getX(), follower.flight.position.getX(), 0.051);
            assertEquals(settled.getZ(), follower.flight.position.getZ(), 0.051);
            assertEquals(0, follower.flight.recoveries);
        }
    }

    private static final class ModeledFollower implements AutoCloseable {
        private final FlightFixture flight;
        private final Field serverField;
        private final Object previousServer;
        private final Location ownerPosition;
        private final Vector movement = new Vector();
        private final V26_2FollowOwnerGoal goal;

        private ModeledFollower() throws Exception {
            this(2);
        }

        private ModeledFollower(final double height) throws Exception {
            flight = new FlightFixture(1);
            flight.wrapMoveControl();
            ownerPosition = flight.position.clone().add(2, -1, 0);
            UUID ownerId = UUID.randomUUID();
            Player owner = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (instance, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> ownerPosition.clone();
                    case "getEyeLocation" -> ownerPosition.clone().add(0, 1.62, 0);
                    case "getVelocity" -> movement.clone();
                    case "getWorld" -> flight.world;
                    case "isOnline", "isOnGround" -> true;
                    case "isDead" -> false;
                    default -> throw new AssertionError(method.getName());
                });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (instance, method, arguments) -> {
                    assertEquals("getPlayer", method.getName());
                    assertEquals(ownerId, arguments[0]);
                    return owner;
                }));
            goal = new V26_2FollowOwnerGoal(flight.craftCarrier(),
                new NmsFollowOwnerSpec(1, ownerId, 8, 2, 1.5, 24, 5, 60, height));
        }

        @Override
        public void close() throws Exception {
            serverField.set(null, previousServer);
            flight.close();
        }
    }
}
