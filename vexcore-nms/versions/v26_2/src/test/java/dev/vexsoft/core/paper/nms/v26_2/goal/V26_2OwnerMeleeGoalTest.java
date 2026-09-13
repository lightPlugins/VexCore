package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.*;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMeleeLeapSpec;
import org.bukkit.util.Vector;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Projectile;
import dev.vexsoft.core.paper.nms.goal.NmsMeleeRangedSpec;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Exercises the native melee goal's target ownership, path retries, and diagnostic behavior. */
public final class V26_2OwnerMeleeGoalTest {

    @Test
    void waterPursuitIsFasterAndLaunchesHigherTowardNearbyShore() {
        Fixture fixture = new Fixture(true, true, true);
        fixture.inWater = true;
        fixture.onGround = false;
        fixture.playerLocation.setX(2);
        fixture.playerLocation.setY(66);
        fixture.start();
        fixture.tick(1);
        assertEquals(1.5, fixture.requestedSpeed);
        assertEquals(1, fixture.impulses.size());
        assertEquals(1.2, fixture.impulses.getFirst().getY());
        int requests = fixture.pathRequests.size();
        fixture.tick(5);
        assertEquals(requests, fixture.pathRequests.size());
        fixture.inWater = false;
        fixture.tick(5);
        assertEquals(requests, fixture.pathRequests.size());
        fixture.onGround = true;
        fixture.tick(1);
        assertTrue(fixture.pathRequests.size() > requests);
        assertEquals(1.0, fixture.requestedSpeed);
        assertEquals(1, fixture.impulses.size());
    }

    @Test
    void submergedCeilingPreventsLaunchAndPreservesRangedFallback() {
        Fixture fixture = new Fixture(true, true, true);
        fixture.inWater = true;
        fixture.onGround = false;
        fixture.clearLeap = false;
        fixture.acceptPath = false;
        fixture.start();
        fixture.tick(60);
        assertTrue(fixture.impulses.isEmpty());
        assertEquals(1, fixture.shots);
    }

    @Test
    void deathRemovesProjectilesAndActiveProjectileCountIsBounded() {
        Fixture fixture = new Fixture(false, true);
        fixture.acceptPath = false;
        fixture.start();
        fixture.tick(1000);
        assertEquals(16, fixture.shots);
        fixture.mobDead = true;
        fixture.tick(1);
        assertEquals(16, fixture.removedProjectiles);
    }

    @Test
    void firesAtIntervalsThroughObstructedSightAndCleansUpOnDespawn() {
        Fixture fixture = new Fixture(false, true);
        fixture.acceptPath = false;
        fixture.lineOfSight = false;
        fixture.start();
        fixture.tick(59);
        assertEquals(0, fixture.shots);
        fixture.tick(1);
        assertEquals(1, fixture.shots);
        fixture.tick(39);
        assertEquals(1, fixture.shots);
        fixture.tick(1);
        assertEquals(2, fixture.shots);
        assertTrue(fixture.damage.isEmpty(), "firing alone must not apply damage");
        assertTrue(fixture.diagnostics.isEmpty());
        fixture.mobValid = false;
        fixture.tick(1);
        assertEquals(2, fixture.removedProjectiles);
        fixture.goal.stop();
        assertEquals(2, fixture.removedProjectiles, "cleanup is idempotent");
    }

    @Test
    void acceptedButStalledPathsAlsoTriggerFallbackAndMovingAgainStopsShooting() {
        Fixture fixture = new Fixture(false, true);
        fixture.start();
        fixture.tick(61);
        assertEquals(1, fixture.shots);
        fixture.mobLocation.setX(1);
        fixture.tick(1);
        fixture.tick(39);
        assertEquals(1, fixture.shots);
        fixture.goal.stop();
        assertEquals(1, fixture.removedProjectiles);
    }

    @Test
    void ownerLossRemovesAllLiveProjectiles() {
        Fixture fixture = new Fixture(false, true);
        fixture.acceptPath = false;
        fixture.start();
        fixture.tick(100);
        assertEquals(2, fixture.shots);
        fixture.online = false;
        fixture.tick(1);
        assertEquals(2, fixture.removedProjectiles);
    }

    @Test
    void distanceLeapStillWorksWithRangedFallbackEnabled() {
        Fixture fixture = new Fixture(true, true);
        fixture.start();
        fixture.tick(1);
        assertEquals(1, fixture.impulses.size());
        assertEquals(0, fixture.shots);
    }

    @Test
    void leapsWithBoundedVelocityAndResumesNavigationAfterLanding() {
        Fixture fixture = new Fixture(true);
        fixture.playerLocation.setX(24);
        fixture.start();
        fixture.tick(1);
        assertEquals(1, fixture.impulses.size());
        assertEquals(1.3, fixture.impulses.getFirst().getX(), 0.00001);
        assertEquals(0.45, fixture.impulses.getFirst().getY());
        fixture.onGround = false;
        int requests = fixture.pathRequests.size();
        fixture.tick(10);
        assertEquals(requests, fixture.pathRequests.size());
        assertEquals(1, fixture.impulses.size());
        fixture.onGround = true;
        fixture.velocity = new Vector();
        fixture.tick(1);
        assertTrue(fixture.pathRequests.size() > requests);
        fixture.tick(48);
        assertEquals(1, fixture.impulses.size());
        fixture.tick(1);
        assertEquals(2, fixture.impulses.size());
        assertTrue(fixture.damage.isEmpty());
    }

    @Test
    void skipsLeapsWhenTooCloseAirborneSubmergedObstructedOrAlreadyLaunched() {
        for (int condition = 0; condition < 7; condition++) {
            Fixture fixture = new Fixture(true);
            switch (condition) {
                case 0 -> fixture.playerLocation.setX(5);
                case 1 -> fixture.onGround = false;
                case 2 -> fixture.inWater = true;
                case 3 -> fixture.clearLeap = false;
                case 4 -> fixture.velocity.setY(0.5);
                case 5 -> fixture.lineOfSight = false;
                case 6 -> fixture.acceptTarget = false;
                default -> throw new AssertionError(condition);
            }
            fixture.start();
            fixture.tick(1);
            assertTrue(fixture.impulses.isEmpty(), "condition " + condition);
        }
    }

    @Test
    void leapUsesHorizontalDirectionAndRejectsLostTargets() {
        Fixture fixture = new Fixture(true);
        fixture.playerLocation.setX(0);
        fixture.playerLocation.setZ(-6);
        fixture.start();
        fixture.tick(1);
        assertEquals(-0.8, fixture.impulses.getFirst().getZ(), 0.00001);
        fixture.online = false;
        fixture.tick(100);
        assertEquals(1, fixture.impulses.size());
    }

    @Test
    void bindsTheOwnerNativelyAndRefreshesThePathToTheMovingPlayer() {
        Fixture fixture = new Fixture();

        fixture.start();
        assertSame(fixture.player, fixture.nativeTarget);
        fixture.tick(1);
        assertEquals(1, fixture.pathRequests.size());
        assertEquals(1.0D, fixture.requestedSpeed);
        fixture.playerLocation.setX(12);
        fixture.tick(10);
        assertEquals(2, fixture.pathRequests.size());
        assertEquals(12, fixture.pathRequests.getLast().getX());
        assertEquals(1, fixture.targetAssignments);
        assertTrue(fixture.goal.requiresUpdateEveryTick());
    }

    @Test
    void diagnosesFailedPathsAfterTwoSecondsAndKeepsRetryingWithoutFloodingTheLog() {
        Fixture fixture = new Fixture();

        fixture.acceptPath = false;
        fixture.start();
        fixture.tick(39);
        assertTrue(fixture.diagnostics.isEmpty());
        fixture.tick(1);
        assertEquals(1, fixture.diagnostics.size());
        assertTrue(fixture.diagnostics.getFirst().contains("path request failed"));
        assertTrue(fixture.diagnostics.getFirst().contains("pathAccepted=false; hasPath=false"));
        assertTrue(fixture.diagnostics.getFirst().contains("movementSpeed=0.25"));
        assertTrue(fixture.diagnostics.getFirst().contains("owner=" + fixture.playerId));
        int requests = fixture.pathRequests.size();

        fixture.tick(1);
        assertEquals(requests + 1, fixture.pathRequests.size());
        fixture.tick(198);
        assertEquals(1, fixture.diagnostics.size());
        fixture.tick(1);
        assertEquals(2, fixture.diagnostics.size());
    }

    @Test
    void restartsAnAcceptedPathWhenTheMobDoesNotActuallyMove() {
        Fixture fixture = new Fixture();

        fixture.start();
        fixture.tick(40);
        assertTrue(fixture.diagnostics.getFirst().contains("path was accepted but the mob made no progress"));
        assertTrue(fixture.diagnostics.getFirst().contains("pathAccepted=true; hasPath=true"));
        assertFalse(fixture.hasPath);
        fixture.tick(1);
        assertTrue(fixture.hasPath);
    }

    @Test
    void movementAndLaunchFailuresDoNotProduceStallWarnings() {
        Fixture fixture = new Fixture();

        fixture.acceptPath = false;
        fixture.onGround = false;
        fixture.start();
        fixture.tick(20);
        fixture.acceptPath = true;
        fixture.onGround = true;
        for (int tick = 0; tick < 200; tick++) {
            fixture.mobLocation.add(0, 0, 0.1D);
            fixture.playerLocation.add(0, 0, 0.1D);
            fixture.tick(1);
        }

        assertTrue(fixture.diagnostics.isEmpty());
        assertTrue(fixture.goal.canContinueToUse());
    }

    @Test
    void attacksKeepConfiguredDamageAndCooldownAndDoNotReportMeleeRangeAsStuck() {
        Fixture fixture = new Fixture();

        fixture.playerLocation.setX(1);
        fixture.start();
        fixture.tick(60);
        assertEquals(List.of(15.0D, 15.0D), fixture.damage);
        assertEquals(2, fixture.swings);
        assertTrue(fixture.pathRequests.isEmpty());
        assertTrue(fixture.diagnostics.isEmpty());
        fixture.playerLocation.setX(8);
        fixture.tick(1);
        assertEquals(1, fixture.pathRequests.size());
    }

    @Test
    void anObstructedPlayerInsideAttackReachStillNeedsAPath() {
        Fixture fixture = new Fixture();

        fixture.playerLocation.setX(1);
        fixture.lineOfSight = false;
        fixture.start();
        fixture.tick(1);
        assertEquals(1, fixture.pathRequests.size());
        assertTrue(fixture.damage.isEmpty());
    }

    @Test
    void rejectedNativeTargetDoesNotAllowCustomAttacksAndIsDiagnosed() {
        Fixture fixture = new Fixture();

        fixture.playerLocation.setX(1);
        fixture.acceptTarget = false;
        fixture.start();
        fixture.tick(40);
        assertNull(fixture.nativeTarget);
        assertTrue(fixture.damage.isEmpty());
        assertTrue(fixture.pathRequests.isEmpty());
        assertTrue(fixture.diagnostics.getFirst().contains("native target was rejected or replaced"));
        fixture.acceptTarget = true;
        fixture.tick(1);
        assertSame(fixture.player, fixture.nativeTarget);
        assertEquals(List.of(15.0D), fixture.damage);
    }

    @Test
    void losingTheOwnerStopsNavigationAndClearsOnlyTheOwnedTarget() {
        Fixture fixture = new Fixture();

        fixture.start();
        fixture.tick(1);
        fixture.online = false;
        assertFalse(fixture.goal.canContinueToUse());
        fixture.tick(1);
        assertFalse(fixture.hasPath);
        assertNull(fixture.nativeTarget);
        fixture.goal.stop();
        assertNull(fixture.nativeTarget);

        fixture.online = true;
        fixture.start();
        LivingEntity foreignTarget = proxy(LivingEntity.class, (proxy, method, args) -> null);

        fixture.nativeTarget = foreignTarget;
        fixture.goal.stop();
        assertSame(foreignTarget, fixture.nativeTarget);
    }

    @Test
    void creativeSpectatorDeadOutOfRangeAndOtherWorldTargetsAreRejected() {
        Fixture fixture = new Fixture();

        fixture.mode = GameMode.CREATIVE;
        assertFalse(fixture.goal.canUse());
        fixture.mode = GameMode.SPECTATOR;
        assertFalse(fixture.goal.canUse());
        fixture.mode = GameMode.SURVIVAL;
        fixture.playerDead = true;
        assertFalse(fixture.goal.canUse());
        fixture.playerDead = false;
        fixture.playerLocation.setX(33);
        assertFalse(fixture.goal.canUse());
        fixture.playerLocation.setX(8);
        fixture.playerLocation.setWorld(world());
        assertFalse(fixture.goal.canUse());
    }

    private static World world() {
        return proxy(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture {

        private final UUID playerId = UUID.randomUUID();
        private final UUID mobId = UUID.randomUUID();
        private final World world = world();
        private final Location mobLocation = new Location(world, 0, 64, 0);
        private final Location playerLocation = new Location(world, 8, 64, 0);
        private final List<Location> pathRequests = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();
        private final List<Double> damage = new ArrayList<>();
        private final Player player;
        private final V26_2OwnerMeleeGoal goal;
        private LivingEntity nativeTarget;
        private boolean online = true;
        private boolean playerDead;
        private boolean lineOfSight = true;
        private boolean acceptTarget = true;
        private boolean acceptPath = true;
        private boolean hasPath;
        private boolean onGround = true;
        private GameMode mode = GameMode.SURVIVAL;
        private int targetAssignments;
        private int swings;
        private double requestedSpeed;
        private final List<Vector> impulses = new ArrayList<>();
        private Vector velocity = new Vector();
        private boolean inWater;
        private boolean clearLeap = true;
        private int shots;
        private int removedProjectiles;
        private boolean mobValid = true;
        private boolean mobDead;

        private Fixture() {
            this(false);
        }

        private Fixture(final boolean leapEnabled) {
            this(leapEnabled, false);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled) {
            this(leapEnabled, rangedEnabled, false);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled) {
            player = proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "isOnline" -> online;
                case "isDead" -> playerDead;
                case "isInWater" -> false;
                case "getGameMode" -> mode;
                case "getWorld" -> playerLocation.getWorld();
                case "getLocation" -> playerLocation.clone();
                case "damage" -> {
                    damage.add((Double) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            Pathfinder pathfinder = proxy(Pathfinder.class, (proxy, method, args) -> switch (method.getName()) {
                case "moveTo" -> {
                    boolean sidePath = args[0] instanceof Location;
                    pathRequests.add(sidePath ? ((Location) args[0]).clone() : ((Player) args[0]).getLocation());
                    requestedSpeed = (double) args[1];
                    hasPath = sidePath || acceptPath;
                    yield hasPath;
                }
                case "hasPath" -> hasPath;
                case "stopPathfinding" -> {
                    hasPath = false;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            Mob mob = proxy(Mob.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> mobId;
                case "getType" -> EntityType.DROWNED;
                case "isValid" -> mobValid;
                case "isDead" -> mobDead;
                case "isInWater" -> inWater;
                case "getVelocity" -> velocity.clone();
                case "setVelocity" -> {
                    velocity = ((Vector) args[0]).clone();
                    impulses.add(velocity.clone());
                    yield null;
                }
                case "isOnGround" -> onGround;
                case "getLocation" -> mobLocation.clone();
                case "getWorld" -> world;
                case "getTarget" -> nativeTarget;
                case "setTarget" -> {
                    targetAssignments++;
                    if (acceptTarget) {
                        nativeTarget = (LivingEntity) args[0];
                    }
                    yield null;
                }
                case "getPathfinder" -> pathfinder;
                case "hasLineOfSight" -> lineOfSight;
                case "swingMainHand" -> {
                    swings++;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });

            goal = new V26_2OwnerMeleeGoal(mob, new NmsOwnerMeleeSpec(2, 1, 32, 2, 15, 30, 10, playerId,
                new NmsMeleeLeapSpec(leapEnabled, 6, 60, 0.8, 1.3, 0.45, waterEnabled, 1.2, 1.4, 1.5),
                new NmsMeleeRangedSpec(rangedEnabled, 60, 40, 0.6, 100)),
                id -> id.equals(playerId) ? player : null, diagnostics::add, () -> 0.25D, impulse -> clearLeap,
                owner -> {
                    assertSame(player, owner);
                    shots++;
                    return proxy(Projectile.class, (proxy, method, args) -> switch (method.getName()) {
                        case "isValid" -> true;
                        case "remove" -> {
                            removedProjectiles++;
                            yield null;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
                });
        }
        private void start() {
            assertTrue(goal.canUse());
            goal.start();
        }

        private void tick(final int count) {
            for (int tick = 0; tick < count; tick++) {
                goal.tick();
            }
        }
    }
}
