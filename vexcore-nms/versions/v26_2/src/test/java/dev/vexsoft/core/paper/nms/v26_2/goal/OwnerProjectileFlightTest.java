package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class OwnerProjectileFlightTest {

    @Test
    void configurableSpeedSweepsAcrossTargetAndHitsOnlyOnce() {
        OwnerProjectileFlight flight = new OwnerProjectileFlight(4, 100);
        int[] impacts = {0};
        int[] removals = {0};
        AABB target = new AABB(2, 0, 0, 3, 2, 1);
        for (int tick = 0; tick < 3; tick++) {
            flight.tick(true, new Vec3(0, 1, 0.5), target, next -> true,
                () -> impacts[0]++, () -> removals[0]++);
        }
        assertEquals(1, impacts[0]);
        assertEquals(1, removals[0]);
    }

    @Test
    void homesTowardCurrentTargetWithConfiguredStepLength() {
        OwnerProjectileFlight flight = new OwnerProjectileFlight(0.6, 100);
        Vec3[] position = {Vec3.ZERO};
        flight.tick(true, position[0], new AABB(10, 0, 0, 11, 1, 1), next -> {
            assertEquals(0.6, next.length(), 0.000001);
            position[0] = next;
            return true;
        }, () -> fail("premature hit"), () -> fail("premature removal"));
        Vec3 before = position[0];
        flight.tick(true, before, new AABB(-10, 0, 0, -9, 1, 1), next -> {
            assertTrue(next.x < before.x);
            assertEquals(0.6, next.distanceTo(before), 0.000001);
            return true;
        }, () -> fail("premature hit"), () -> fail("premature removal"));
    }

    @Test
    void invalidOwnerOrExpiredLifetimeRemovesWithoutDamage() {
        for (boolean valid : new boolean[] {false, true}) {
            OwnerProjectileFlight flight = new OwnerProjectileFlight(0.1, 1);
            int[] removed = {0};
            for (int tick = 0; tick < 3; tick++) {
                flight.tick(valid, Vec3.ZERO, new AABB(10, 0, 0, 11, 1, 1), next -> true,
                    () -> fail("unexpected damage"), () -> removed[0]++);
            }
            assertEquals(1, removed[0]);
        }
    }
}
