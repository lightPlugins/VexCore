package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import org.junit.jupiter.api.Test;

/** Pause suppresses autonomous hovering while caller-owned retreat and gathering orders keep working. */
final class V26_2FlightRetreatTest {

    @Test
    void pausedGoalsStillAcceptNativeRetreatWaypoints() throws Exception {
        try (var flight = new FlightFixture(1)) {
            NmsMobGoalControl.setPaused(flight.carrier, true);
            for (int tick = 0; tick < 3; tick++) {
                flight.nativeMob.tickCount++;
                flight.control.setWantedPosition(10, 65, 0.5, 8);
                flight.control.tick();
            }
            assertTrue(flight.nativeMob.getDeltaMovement().x > 0.2);
            assertTrue(NmsMobGoalControl.isPaused(flight.carrier));
        }
    }

    @Test
    void pausedIdleCarrierDoesNotStartAutonomousAscent() throws Exception {
        try (var flight = new FlightFixture(1)) {
            flight.position.setY(64);
            NmsMobGoalControl.setPaused(flight.carrier, true);
            flight.tick();
            assertEquals(64, flight.position.getY());
        }
    }

    @Test
    void explicitOrdersContinueWhileBehaviorGoalsArePaused() throws Exception {
        try (var flight = new FlightFixture(1)) {
            NmsMobGoalControl.setPaused(flight.carrier, true);
            flight.control.flyTo(flight.position.clone().add(4, 0, 0), 8);
            flight.tick();
            assertTrue(flight.position.getX() > 0.5);
        }
    }

    @Test
    void desiredHoverHeightDescendsFromAboveTheLocalMinimum() throws Exception {
        var profile = new dev.vexsoft.core.api.mob.flight.FlightProfile(8, 40, 50, 540, 0.05, 1);
        try (var flight = new FlightFixture(1, profile)) {
            flight.position.setY(68);
            for (int tick = 0; tick < 100; tick++) {
                flight.tick();
            }
            assertEquals(65, flight.position.getY(), 0.051);
        }
    }

    @Test
    void idleCarrierStillMaintainsItsMinimumHeight() throws Exception {
        try (var flight = new FlightFixture(1)) {
            flight.position.setY(64);
            flight.tick();
            assertTrue(flight.position.getY() > 64);
        }
    }
}
