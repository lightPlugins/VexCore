package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Exercises explicit owner resolution, movement hysteresis and bounded native path refresh. */
public final class V26_2FollowOwnerGoalTest {

    @Test
    void followsOnlyTheConfiguredOwnerAndStopsAtTheInnerDistance() {
        UUID ownerId = UUID.randomUUID();
        World world = proxy(World.class, (instance, method, arguments) -> {
            if (method.getName().equals("equals")) {
                return instance == arguments[0];
            }
            throw new AssertionError(method.getName());
        });
        Location position = new Location(world, 0, 64, 0);
        Location target = new Location(world, 5, 64, 0);
        var stops = new AtomicInteger();
        var paths = new AtomicInteger();
        var velocity = new AtomicReference<>(new Vector(0.4D, 0.2D, -0.6D));
        Pathfinder pathfinder = proxy(Pathfinder.class, (instance, method, arguments) -> {
            if (method.getName().equals("moveTo")) {
                paths.incrementAndGet();
                return true;
            }
            if (method.getName().equals("stopPathfinding")) {
                stops.incrementAndGet();
                return null;
            }
            throw new AssertionError(method.getName());
        });
        Mob mob = proxy(Mob.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getLocation" -> position.clone();
            case "getWorld" -> world;
            case "getUniqueId" -> ownerId;
            case "isValid" -> true;
            case "getPathfinder" -> pathfinder;
            case "getVelocity" -> velocity.get().clone();
            case "setVelocity" -> {
                velocity.set(((Vector) arguments[0]).clone());
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        Player owner = proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getLocation", "getEyeLocation" -> target.clone();
            case "getWorld" -> world;
            case "isOnline" -> true;
            case "isDead" -> false;
            default -> throw new AssertionError(method.getName());
        });
        var goal = new V26_2FollowOwnerGoal(mob, new NmsFollowOwnerSpec(1, ownerId, 1.2, 4, 2, 24, 10, 60),
            id -> {
                assertEquals(ownerId, id);
                return owner;
            }, ignored -> {
            });
        assertTrue(goal.canUse());
        goal.start();
        for (int tick = 0; tick < 10; tick++) {
            goal.tick();
        }
        assertEquals(1, paths.get());
        goal.tick();
        assertEquals(2, paths.get());
        assertEquals(new Vector(0.4D, 0.2D, -0.6D), velocity.get());
        goal.stop();
        assertEquals(new Vector(0.4D, 0.2D, -0.6D), velocity.get(),
            "An interrupted pursuit must not brake before reaching the owner");
        assertTrue(goal.canUse());
        goal.start();
        target.setX(3);
        assertTrue(goal.canContinueToUse());
        target.setX(2);
        assertFalse(goal.canContinueToUse());
        goal.stop();
        assertEquals(2, stops.get());
        assertEquals(new Vector(0.2D, 0.1D, -0.3D), velocity.get(),
            "Reaching the inner distance must halve residual momentum");
        assertFalse(goal.canUse());
        assertEquals(new Vector(0.2D, 0.1D, -0.3D), velocity.get(),
            "Idle goal checks must not repeatedly damp gravity or movement");
    }

    @Test
    void missingOrDeadOwnerNeverStartsNavigation() {
        var mob = proxy(Mob.class, (instance, method, arguments) -> {
            if (method.getName().equals("isValid")) {
                return true;
            }
            throw new AssertionError(method.getName());
        });
        var goal = new V26_2FollowOwnerGoal(mob,
            new NmsFollowOwnerSpec(1, UUID.randomUUID(), 1.2, 4, 2, 24, 10, 60), id -> null, ignored -> {
            });
        assertFalse(goal.canUse());
        assertFalse(goal.canContinueToUse());
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
