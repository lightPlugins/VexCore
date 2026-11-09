package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.mob.flight.FlightBounds;
import dev.vexsoft.core.api.mob.flight.FlightObstacles;
import dev.vexsoft.core.api.mob.flight.FlightState;
import org.junit.jupiter.api.Test;

/** Runs actual motor ticks, including native motion fields, arrival and obstacle rejection. */
final class V26_2FlightSpeedTest {

    @Test
    void longCanopyOrdersKeepFlyingBeyondTheNativeDirectRouteLimit() throws Exception {
        try (var flight = new FlightFixture(0)) {
            flight.leaves = true;
            var target = flight.position.clone().add(40, 0, 0);
            flight.control.flyTo(target, 8, new FlightBounds(20.5, 0.5, 40, 64, 70),
                FlightObstacles.IGNORE_LEAVES);
            for (int tick = 0; tick < 140; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.distance(target) < 0.051);
            assertEquals(0, flight.pathRequests);
        }
    }

    @Test
    void leafOrdersUseNativePhysicsOverrideButStillRejectWallsLiquidsAndUnloadedChunks() throws Exception {
        try (var flight = new FlightFixture(1)) {
            flight.leaves = true;
            var bounds = new FlightBounds(0.5, 0.5, 10, 64, 70);
            var target = flight.position.clone().add(4, 0, 0);
            flight.control.flyTo(target, 8, bounds, FlightObstacles.IGNORE_LEAVES);
            for (int tick = 0; tick < 50; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.distance(target) < 0.051);
            assertTrue(flight.nativeMob.noPhysics, "Native travel must actually pass the leaves");
            assertEquals(65, flight.position.getY(), 0.000001);
            flight.control.release();
            assertFalse(flight.nativeMob.noPhysics, "Releasing work must restore the previous native physics");
        }
        for (int obstruction = 0; obstruction < 3; obstruction++) {
            try (var flight = new FlightFixture(0)) {
                flight.wall = obstruction == 0;
                flight.leaves = obstruction == 1;
                flight.liquid = obstruction == 1;
                flight.loaded = obstruction != 2;
                flight.control.flyTo(flight.position.clone().add(4, 0, 0), 8, null,
                    FlightObstacles.IGNORE_LEAVES);
                for (int tick = 0; tick < 30; tick++) {
                    flight.tick();
                }
                assertTrue(flight.position.getX() < 1.7);
            }
        }
    }

    @Test
    void boundedWorkHoldsDoNotGetLiftedByTheFollowHoverClearance() throws Exception {
        try (var flight = new FlightFixture(1)) {
            flight.position.setY(64);
            var bounds = new FlightBounds(0.5, 0.5, 10, 64, 70);
            flight.control.flyTo(flight.position.clone().add(4, 0, 0), 8, bounds);
            for (int tick = 0; tick < 30; tick++) {
                flight.tick();
            }
            assertEquals(64, flight.position.getY(), 0.000001);
        }
    }

    @Test
    void nativeDragDoesNotChangeTheConfiguredTimeToCruisingSpeed() throws Exception {
        try (var flight = new FlightFixture(0)) {
            flight.control.flyTo(flight.position.clone().add(20, 0, 0), 8);
            for (int tick = 1; tick <= 4; tick++) {
                flight.tick();
                assertEquals(tick * 0.1 * 0.91, flight.nativeMob.getDeltaMovement().x, 0.000001);
                assertEquals(0, flight.nativeMob.xxa);
                assertEquals(0, flight.nativeMob.yya);
                assertEquals(0, flight.nativeMob.zza);
            }
            assertEquals(1.5, flight.position.getX(), 0.000001);
        }
    }

    @Test
    void lateNavigationWaypointsCannotReplaceTheCurrentFollowTarget() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var oldTarget = flight.position.clone();
            var followTarget = oldTarget.clone().add(8, 0, 0);
            flight.control.track(oldTarget, net.minecraft.world.phys.Vec3.ZERO, 8);
            flight.tick();
            for (int tick = 0; tick < 20; tick++) {
                flight.control.track(followTarget, net.minecraft.world.phys.Vec3.ZERO, 8);
                // Minecraft ticks navigation after goals, so a queued path may still submit an old waypoint.
                flight.control.setWantedPosition(oldTarget.getX(), oldTarget.getY(), oldTarget.getZ(), 8);
                flight.tick();
            }
            assertTrue(flight.position.getX() > oldTarget.getX() + 5,
                "Following must retain its current target after the navigation phase");
        }
    }

    @Test
    void failedNewPursuitBrakesInsteadOfContinuingTowardThePreviousPoint() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var oldTarget = flight.position.clone().add(0, 0, 6);
            flight.control.track(oldTarget, net.minecraft.world.phys.Vec3.ZERO, 8);
            for (int tick = 0; tick < 10; tick++) {
                flight.tick();
            }
            flight.wall = true;
            var blockedAt = flight.position.clone();
            assertFalse(flight.control.track(blockedAt.clone().add(4, 0, 0),
                net.minecraft.world.phys.Vec3.ZERO, 8));
            for (int tick = 0; tick < 20; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.distance(blockedAt) <= 0.46,
                "A failed new target may brake, but must not continue chasing the old target");
        }
    }

    @Test
    void stoppingThePreviousFollowGoalCannotCancelTheNewExplicitOrder() throws Exception {
        try (var flight = new FlightFixture(0)) {
            flight.control.track(flight.position.clone().add(-6, 0, 0),
                net.minecraft.world.phys.Vec3.ZERO, 8);
            flight.tick();
            var target = flight.position.clone().add(8, 0, 0);
            flight.control.flyTo(target, 8);
            // The goal selector stops the old follow goal after the gathering order has taken ownership.
            flight.control.stopTracking();
            for (int tick = 0; tick < 20; tick++) {
                flight.tick();
            }
            assertTrue(flight.position.getX() > target.getX() - 3);
        }
    }

    @Test
    void recoveryPlacementRetiresAllPreviousOrdersInsteadOfFlyingBack() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var oldTarget = flight.position.clone().add(4, 0, 0);
            flight.control.flyTo(oldTarget, 8);
            flight.tick();
            flight.position.add(20, 0, 0);
            var recovered = flight.position.clone();
            flight.control.resetAfterPlacement();
            for (int tick = 0; tick < 10; tick++) {
                flight.tick();
            }
            assertEquals(recovered.getX(), flight.position.getX(), 0.000001,
                "A recovery teleport must retire the previous flight destination");
        }
    }

    @Test
    void repeatedOrdersSettleWithoutTeleportingOrResettingAcceleration() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var target = flight.position.clone().add(4, 0, 0);
            FlightState state = FlightState.MOVING;
            for (int tick = 0; tick < 200 && state != FlightState.ARRIVED; tick++) {
                state = flight.control.flyTo(target, 8);
                flight.tick();
                assertTrue(flight.position.getX() <= target.getX() + 0.000001);
            }
            assertEquals(FlightState.ARRIVED, state);
            assertTrue(flight.position.distance(target) <= 0.050001);
        }
    }

    @Test
    void explicitNativeImpulsesArePreservedAndThenControlledByTheSameAccelerationLimits() throws Exception {
        try (var flight = new FlightFixture(0)) {
            flight.control.flyTo(flight.position.clone().add(10, 0, 0), 8);
            flight.tick();
            flight.nativeMob.setDeltaMovement(new net.minecraft.world.phys.Vec3(-0.3, 0, 0));
            flight.nativeMob.hurtMarked = true;
            flight.tick();
            assertTrue(flight.nativeMob.getDeltaMovement().x < 0,
                "A native knockback/launch must not disappear when the motor ticks");
            assertEquals(-0.2 * 0.91, flight.nativeMob.getDeltaMovement().x, 0.000001);
        }
    }

    @Test
    void smallRetargetsInvalidateArrivedStateAndHoldingBrakesGradually() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var target = flight.position.clone();
            assertEquals(FlightState.ARRIVED, flight.control.flyTo(target, 8));
            assertEquals(FlightState.MOVING, flight.control.flyTo(target.clone().add(0.09, 0, 0), 8));
            flight.control.flyTo(target.clone().add(10, 0, 0), 8);
            for (int tick = 0; tick < 4; tick++) {
                flight.tick();
            }
            flight.control.flyTo(flight.position, 0);
            flight.tick();
            assertEquals(0.275 * 0.91, flight.nativeMob.getDeltaMovement().x, 0.000001);
        }
    }

    @Test
    void boundedOrdersNeverLeaveTheirConfiguredVolume() throws Exception {
        try (var flight = new FlightFixture(0)) {
            var bounds = new FlightBounds(0.5, 0.5, 1, 64, 66);
            var target = flight.position.clone().add(4, 0, 0);
            flight.control.flyTo(target, 8, bounds);
            flight.tick();
            assertEquals(0.5, flight.position.getX(), 0.000001);
            assertEquals(FlightState.BLOCKED, flight.control.flyTo(target, 8, bounds));
        }
    }

    @Test
    void unavailableOrObstructedRoutesNeverFlyThroughBlocks() throws Exception {
        for (boolean loaded : new boolean[]{false, true}) {
            try (var flight = new FlightFixture(0)) {
                flight.loaded = loaded;
                flight.wall = loaded;
                var target = flight.position.clone().add(4, 0, 0);
                flight.control.flyTo(target, 8);
                flight.tick();
                assertEquals(0.5, flight.position.getX(), 0.000001);
                assertEquals(FlightState.BLOCKED, flight.control.flyTo(target, 8));
                assertEquals(loaded ? 1 : 0, flight.pathRequests);
            }
        }
    }
}
