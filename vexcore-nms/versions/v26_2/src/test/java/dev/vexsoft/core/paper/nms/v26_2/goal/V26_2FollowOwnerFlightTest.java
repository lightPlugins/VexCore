package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Verifies airborne pursuit, idle altitude correction and recovery without requiring a running server. */
final class V26_2FollowOwnerFlightTest {

    @Test
    void flyingPursuitUsesOwnerFeetOffsetAndTheConfiguredSpeed() {
        var fixture = new Follower();
        fixture.ownerPosition.setX(6);
        var goal = fixture.goal(true, 1.75, 2.5);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertEquals(new Location(fixture.world, 6, 65.75, 0), fixture.pathTarget);
        assertEquals(2.5D, fixture.speed);
        assertEquals(64.0D, fixture.ownerPosition.getY(), "Building a target must not mutate the owner's position");
    }

    @Test
    void stationaryOwnerStillGetsFractionalHeightCorrectionAndHeightDoesNotTriggerEndlessPursuit() {
        var fixture = new Follower();
        fixture.ownerPosition.setX(1);
        var goal = fixture.goal(true, 8.75, 1.8);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertEquals(new Location(fixture.world, 0, 72.75, 0), fixture.hoverTarget);
        assertEquals(1.8D, fixture.speed);
        assertNull(fixture.pathTarget);
        fixture.position.setY(72.75);
        assertFalse(goal.canContinueToUse());
        goal.stop();
        assertFalse(goal.canUse(), "Distance must be measured to the offset target, not the owner's feet");
        fixture.ownerPosition.setY(65);
        assertTrue(goal.canUse(), "An owner moving vertically must refresh the pet's height even nearby");
    }

    @Test
    void groundCarriersIgnoreTheFlightOffset() {
        var fixture = new Follower();
        fixture.ownerPosition.setX(6);
        var goal = fixture.goal(false, 8.75, 2.5);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertEquals(new Location(fixture.world, 6, 64, 0), fixture.pathTarget);
        assertEquals(2.5D, fixture.speed);
        fixture.ownerPosition.setX(1);
        assertFalse(goal.canContinueToUse());
        assertNull(fixture.hoverTarget);
    }

    @Test
    void recoveryKeepsTheFlightHeightAndRejectsBlockedOrUnloadedDestinations() {
        var fixture = new Follower();
        fixture.ownerPosition.setX(40);
        var goal = fixture.goal(true, 2.75, 1.2);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertEquals(66.75D, fixture.teleported.getY());
        assertEquals(new Vector(), fixture.velocity);
        assertNull(fixture.pathTarget);

        for (boolean unloaded : new boolean[]{false, true}) {
            var blocked = new Follower();
            blocked.ownerPosition.setX(40);
            blocked.loaded = !unloaded;
            blocked.blocked = !unloaded;
            var blockedGoal = blocked.goal(true, 2.75, 1.2);
            assertTrue(blockedGoal.canUse());
            blockedGoal.start();
            blockedGoal.tick();
            assertNull(blocked.teleported);
        }
    }

    @Test
    void pausedPetDoesNotStartHeightCorrection() {
        var fixture = new Follower();
        NmsMobGoalControl.setPaused(fixture.mob, true);
        try {
            assertFalse(fixture.goal(true, 2.75, 1.2).canUse());
        } finally {
            NmsMobGoalControl.setPaused(fixture.mob, false);
        }
    }

    private static final class Follower {
        private final UUID id = UUID.randomUUID();
        private boolean loaded = true;
        private boolean blocked;
        private final World world = proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
            case "equals" -> instance == arguments[0];
            case "isChunkLoaded" -> loaded;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getBlockAt" -> {
                assertTrue(loaded);
                yield proxy(Block.class, (block, call, values) -> switch (call.getName()) {
                    case "isPassable" -> !blocked;
                    case "isLiquid" -> false;
                    default -> throw new AssertionError(call.getName());
                });
            }
            default -> throw new AssertionError(method.getName());
        });
        private final Location position = new Location(world, 0, 64, 0);
        private final Location ownerPosition = new Location(world, 0, 64, 0);
        private Location pathTarget;
        private Location hoverTarget;
        private Location teleported;
        private double speed;
        private Vector velocity = new Vector(0.4, 0.2, -0.6);
        private final Pathfinder pathfinder = proxy(Pathfinder.class, (instance, method, arguments) -> {
            if (method.getName().equals("moveTo")) {
                pathTarget = ((Location) arguments[0]).clone();
                speed = (double) arguments[1];
                return true;
            }
            if (method.getName().equals("stopPathfinding")) {
                return null;
            }
            throw new AssertionError(method.getName());
        });
        private final Mob mob = proxy(Mob.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getLocation" -> position.clone();
            case "getWorld" -> world;
            case "getUniqueId" -> id;
            case "isValid" -> true;
            case "getPathfinder" -> pathfinder;
            case "getBoundingBox" -> new BoundingBox(-0.35, 64, -0.35, 0.35, 64.6, 0.35);
            case "getVelocity" -> velocity.clone();
            case "setVelocity" -> {
                velocity = ((Vector) arguments[0]).clone();
                yield null;
            }
            case "teleport" -> {
                teleported = ((Location) arguments[0]).clone();
                yield true;
            }
            default -> throw new AssertionError(method.getName());
        });
        private final Player owner = proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getLocation" -> ownerPosition.clone();
            case "getWorld" -> world;
            case "isOnline" -> true;
            case "isDead" -> false;
            default -> throw new AssertionError(method.getName());
        });

        private V26_2FollowOwnerGoal goal(final boolean flying, final double offset, final double pursuitSpeed) {
            return new V26_2FollowOwnerGoal(mob,
                new NmsFollowOwnerSpec(1, id, pursuitSpeed, 4, 2, 24, 10, 60, offset),
                ignored -> owner, ignored -> {
                }, flying, (target, value) -> {
                    hoverTarget = target.clone();
                    speed = value;
                });
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
