package dev.vexsoft.core.api.mob.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavioral checks for the physical controller used by every managed flight feature. */
final class FlightMotionTest {

    private final FlightProfile profile = FlightProfile.defaults();

    @Test
    void reachesEightBlocksPerSecondInFourTicksWithoutNativeMultipliers() {
        FlightVector velocity = FlightVector.ZERO;
        for (int tick = 1; tick <= 4; tick++) {
            velocity = FlightMotion.advance(new FlightVector(100, 0, 0), velocity, FlightVector.ZERO, 8, profile);
            assertEquals(tick * 0.1, velocity.x(), 0.000001);
        }
        assertEquals(0.4, velocity.length(), 0.000001);
    }

    @Test
    void brakesAndArrivesWithoutCrossingStationaryGoalsAtDifferentDistances() {
        for (double distance : new double[]{0.01, 0.06, 0.5, 2, 20}) {
            FlightVector position = FlightVector.ZERO;
            FlightVector target = new FlightVector(distance, 0, 0);
            FlightVector velocity = FlightVector.ZERO;
            boolean arrived = false;
            for (int tick = 0; tick < 1000; tick++) {
                FlightVector previous = velocity;
                velocity = FlightMotion.advance(target.subtract(position), velocity, FlightVector.ZERO, 8, profile);
                assertTrue(velocity.subtract(previous).length() <= profile.deceleration() / 400 + 0.000001);
                position = position.add(velocity);
                assertTrue(position.x() <= distance + 0.000001, "Crossed the goal");
                if (FlightMotion.arrived(target.subtract(position).length(), velocity, profile)) {
                    arrived = true;
                    break;
                }
            }
            assertTrue(arrived, "Did not settle at distance " + distance);
        }
    }

    @Test
    void movingTargetsRetainTheirVelocityInsteadOfRepeatedStopAndGo() {
        FlightVector owner = new FlightVector(2, 0, 0);
        FlightVector ownerVelocity = new FlightVector(0.25, 0, 0);
        FlightVector position = FlightVector.ZERO;
        FlightVector velocity = FlightVector.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            owner = owner.add(ownerVelocity);
            velocity = FlightMotion.advance(owner.subtract(position), velocity, ownerVelocity, 8, profile);
            position = position.add(velocity);
        }
        assertEquals(ownerVelocity.x(), velocity.x(), 0.00001);
        assertTrue(owner.subtract(position).length() < 0.3);
    }

    @Test
    void aNewOrderChangesDirectionWithoutDiscardingMomentum() {
        FlightVector previous = new FlightVector(0.4, 0, 0);
        FlightVector next = FlightMotion.advance(new FlightVector(-10, 0, 0), previous, FlightVector.ZERO, 8, profile);
        assertTrue(next.x() > 0, "Changing a goal must not reset or instantly reverse velocity");
        assertTrue(previous.subtract(next).length() <= profile.deceleration() / 400 + 0.000001);
    }

    @Test
    void allRequestedSpeedsRespectOnePhysicalCapAndZeroSpeedBrakes() {
        FlightVector velocity = FlightVector.ZERO;
        for (int tick = 0; tick < 20; tick++) {
            velocity = FlightMotion.advance(new FlightVector(100, 0, 0), velocity, FlightVector.ZERO, 100, profile);
        }
        assertEquals(profile.maxSpeed() / 20, velocity.length(), 0.000001);
        for (int tick = 0; tick < 4; tick++) {
            velocity = FlightMotion.advance(FlightVector.ZERO, velocity, FlightVector.ZERO, 0, profile);
        }
        assertEquals(FlightVector.ZERO, velocity);
    }

    @Test
    void turningUsesTheShortestArcAndTheSameDegreesPerSecondLimit() {
        assertEquals(190, FlightMotion.heading(170, -170, profile), 0.00001);
        assertEquals(-27, FlightMotion.heading(0, -90, profile), 0.00001);
    }

    @Test
    void holdingDuringTravelUsesTheConfiguredBrakeInsteadOfAnInstantStop() {
        var previous = new FlightVector(0.4, 0, 0);
        var next = FlightMotion.advance(FlightVector.ZERO, previous, FlightVector.ZERO, 0, profile);
        assertEquals(0.275, next.x(), 0.000001);
    }

    @Test
    void intermediateWaypointsDoNotCauseStopAndGo() {
        var cruise = new FlightVector(0.4, 0, 0);
        var next = FlightMotion.advance(new FlightVector(0.2, 0, 0), cruise, FlightVector.ZERO, 8, profile, false);
        assertEquals(cruise, next);
    }

    @Test
    void invalidPhysicalProfilesAndRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FlightProfile(8, 0, 50, 540, 0.05, 1));
        assertThrows(IllegalArgumentException.class, () -> new FlightProfile(Double.NaN, 40, 50, 540, 0.05, 1));
        assertThrows(IllegalArgumentException.class, () -> FlightMotion.advance(FlightVector.ZERO,
            FlightVector.ZERO, FlightVector.ZERO, Double.NaN, profile));
    }
}
