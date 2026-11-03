package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Exercises the real follow goal and motor across Minecraft's goal/navigation/control tick phases. */
final class V26_2FollowOwnerFlightMotorTest {

    @Test
    void walkingStoppingAndTurningOwnersAreFollowedWithoutRecoveryOrFlyingBackToAnOldPoint() throws Exception {
        try (var flight = new FlightFixture(1)) {
            Location ownerPosition = flight.position.clone().add(2, -1, 0);
            Vector movement = new Vector();
            Player owner = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> ownerPosition.clone();
                    case "getVelocity" -> movement.clone();
                    case "getWorld" -> flight.world;
                    case "isOnline" -> true;
                    case "isDead" -> false;
                    default -> throw new AssertionError(method.getName());
                });
            var goal = new V26_2FollowOwnerGoal(flight.carrier,
                new NmsFollowOwnerSpec(1, UUID.randomUUID(), 8, 2, 1.5, 24, 5, 60, 2),
                ignored -> owner, ignored -> {
                }, true, (target, speed) -> flight.control.track(target,
                    new Vec3(movement.getX(), movement.getY(), movement.getZ()), speed));
            assertTrue(goal.canUse());
            goal.start();
            Location obsoleteWaypoint = flight.position.clone();
            for (int tick = 0; tick < 600; tick++) {
                double horizontalSpeed = tick < 200 ? 0.2 : tick < 320 ? 0 : tick < 500 ? -0.2 : 0;
                movement.setX(horizontalSpeed);
                ownerPosition.add(movement);
                goal.tick();
                // Native navigation runs after goals and must not reclaim direct following.
                flight.control.setWantedPosition(obsoleteWaypoint.getX(), obsoleteWaypoint.getY(),
                    obsoleteWaypoint.getZ(), 0);
                flight.tick();
                assertTrue(flight.position.distance(ownerPosition) < 6,
                    "The follower fell behind while its current owner position was available");
                assertTrue(goal.canContinueToUse());
            }
        }
    }
}
