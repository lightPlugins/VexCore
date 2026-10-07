package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks pursuit factors against Minecraft's actual input normalization rather than only pathfinder arguments. */
final class V26_2FlightSpeedTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void highMeleeSpeedIncreasesActualAirAccelerationPastTheVanillaLimit() {
        Vec3 slow = input(0.6, new Vec3(100, 0, 0));
        Vec3 fast = input(6.3, new Vec3(100, 0, 0));
        Vec3 vanillaFast = NativeInput.movement(fast, 0.02F, 0);
        assertEquals(0.02D, vanillaFast.z, 1.0E-8D, "Vanilla clamps even a 10.5 pursuit multiplier");
        Vec3 slowImpulse = impulse(slow, 0);
        Vec3 fastImpulse = impulse(fast, 0);
        assertEquals(10.5D, fastImpulse.z / slowImpulse.z, 1.0E-5D);
        assertTrue(fastImpulse.z > vanillaFast.z * 6);
    }

    @Test
    void petSpeedFactorsRemainProportionalAfterMultipleNativeDragTicks() {
        double baselineDistance = distance(1.0D);
        assertEquals(1.5D, distance(1.5D) / baselineDistance, 1.0E-5D);
        assertEquals(3.0D, distance(3.0D) / baselineDistance, 1.0E-5D);
        assertEquals(10.0D, distance(10.0D) / baselineDistance, 1.0E-5D);
    }

    @Test
    void correctionPreservesNativeYawAndDoesNotAccelerateIdleInput() {
        Vec3 forward = impulse(new Vec3(0, 0, 6), 90);
        assertEquals(-0.12D, forward.x, 1.0E-7D);
        assertEquals(0.0D, forward.z, 1.0E-7D);
        assertEquals(Vec3.ZERO, impulse(Vec3.ZERO, 90));
        assertEquals(0.0F, V26_2FlightMoveControl.additionalAirAcceleration(new Vec3(0, 0, 0.6)));
    }

    @Test
    void hoveringDoesNotProduceForwardDriftOrFullVerticalInputAtEqualHeight() {
        Vec3 nativeInput = new Vec3(0, -6, 6);
        assertEquals(new Vec3(0, 0, 6),
            V26_2FlightMoveControl.airInput(nativeInput, new Vec3(100, 0, 0), 6));
        assertEquals(new Vec3(0, 0.1, 0),
            V26_2FlightMoveControl.airInput(nativeInput, new Vec3(0, 0.1, 0), 6));
        assertEquals(new Vec3(0, -0.1, 0),
            V26_2FlightMoveControl.airInput(nativeInput, new Vec3(0, -0.1, 0), 6));
        assertEquals(0.0D, impulse(input(6, new Vec3(0, 3, 0)), 0).horizontalDistanceSqr());
    }

    private static Vec3 input(final double speed, final Vec3 targetDelta) {
        return V26_2FlightMoveControl.airInput(new Vec3(0, -speed, speed), targetDelta, speed);
    }

    private static Vec3 impulse(final Vec3 input, final float yaw) {
        return NativeInput.movement(input, 0.02F, yaw).add(NativeInput.movement(input,
            V26_2FlightMoveControl.additionalAirAcceleration(input), yaw));
    }

    private static double distance(final double factor) {
        Vec3 input = input(0.6D * factor, new Vec3(100, 0, 0));
        Vec3 velocity = Vec3.ZERO;
        double distance = 0.0D;
        for (int tick = 0; tick < 60; tick++) {
            velocity = velocity.add(impulse(input, 0));
            distance += velocity.z;
            velocity = velocity.scale(0.91F);
        }
        return distance;
    }

    /** Exposes the same protected rotation and normalization used by Entity.moveRelative without spawning a carrier. */
    private abstract static class NativeInput extends Entity {

        private NativeInput(final EntityType<?> type, final Level level) {
            super(type, level);
        }

        private static Vec3 movement(final Vec3 input, final float acceleration, final float yaw) {
            return getInputVector(input, acceleration, yaw);
        }
    }
}
