package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.*;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.api.mob.MobMeleeAttackSequence;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.goal.NmsMeleeLeapSpec;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
    void globalScopeSelectsNearestEligiblePlayer() {
        Player[] candidates = new Player[2];
        World world = proxy(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "getPlayers" -> List.of(candidates);
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> throw new AssertionError(method.getName());
        });
        candidates[0] = eligiblePlayer(new Location(world, 8, 64, 0));
        candidates[1] = eligiblePlayer(new Location(world, 3, 64, 0));
        LivingEntity[] selected = new LivingEntity[1];
        UUID mobId = UUID.randomUUID();
        Mob mob = proxy(Mob.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> mobId;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "isValid" -> true;
            case "isDead" -> false;
            case "getTarget" -> selected[0];
            case "setTarget" -> {
                selected[0] = (LivingEntity) args[0];
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        V26_2OwnerMeleeGoal goal = new V26_2OwnerMeleeGoal(
            mob, new NmsOwnerMeleeSpec(2, 1, 32, 2, 15, 30, 10, null), id -> null,
            ignored -> { }, () -> 0.25D
        );

        assertTrue(goal.canUse());
        goal.start();
        assertSame(candidates[1], selected[0]);
        NmsMobGoalControl.setPaused(mob, true);
        assertFalse(goal.canContinueToUse());
        assertFalse(goal.canUse());
        NmsMobGoalControl.setPaused(mob, false);
    }

    @Test
    void scopedMobAcquiresWithinAggroRadiusAndPursuesWithinFullRadius() {
        Fixture fixture = new Fixture(false, false, false, 0, 12);
        fixture.playerLocation.setX(20);
        assertFalse(fixture.goal.canUse());

        fixture.playerLocation.setX(10);
        assertTrue(fixture.goal.canUse());
        fixture.goal.start();

        fixture.playerLocation.setX(20);
        assertTrue(fixture.goal.canContinueToUse());

        fixture.playerLocation.setX(33);
        assertFalse(fixture.goal.canContinueToUse());
    }

    private static Player eligiblePlayer(final Location location) {
        return proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "isOnline" -> true;
            case "isDead" -> false;
            case "getGameMode" -> GameMode.SURVIVAL;
            case "getLocation" -> location.clone();
            case "getWorld" -> location.getWorld();
            default -> throw new AssertionError(method.getName());
        });
    }

    @Test
    void waterPursuitIsFasterAndLaunchesHigherTowardNearbyShore() {
        Fixture fixture = new Fixture(true, true, true);
        fixture.inWater = true;
        fixture.onGround = false;
        fixture.playerLocation.setX(3);
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
        assertTrue(fixture.diagnostics.isEmpty());
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
    void scaledMobAttacksWhenHitboxesAreWithinReachDespiteDistantEntityOrigins() {
        Fixture fixture = new Fixture();
        fixture.mobWidth = 2.0D;
        fixture.playerLocation.setX(2.58D);
        fixture.start();
        fixture.tick(40);

        assertEquals(List.of(15.0D, 15.0D), fixture.damage);
        assertTrue(fixture.pathRequests.isEmpty());
        assertTrue(fixture.diagnostics.isEmpty());
    }

    @Test
    void passiveMobWaitsForProvocationAndStopsWhenAngerIsCleared() {
        UUID[] angerTarget = {null};
        Fixture fixture = new Fixture(true, angerTarget);
        fixture.playerLocation.setX(1);

        assertFalse(fixture.goal.canUse());
        angerTarget[0] = fixture.playerId;
        assertTrue(fixture.goal.canUse());
        fixture.goal.start();
        fixture.tick(1);
        assertEquals(List.of(15.0D), fixture.damage);

        angerTarget[0] = null;
        assertFalse(fixture.goal.canContinueToUse());
    }

    @Test
    void animatedAttackStopsMovementUntilFinishedAndHitsAfterDelay() {
        boolean[] finished = {false};
        boolean[] cancelled = {false};
        MobMeleeAttackSequence sequence = (mobId, targetId) -> new MobMeleeAttackSequence.Attack() {

            @Override
            public int damageDelayTicks() {
                return 20;
            }

            @Override
            public boolean isFinished() {
                return finished[0];
            }

            @Override
            public void cancel() {
                cancelled[0] = true;
            }
        };
        Fixture fixture = new Fixture(sequence);
        fixture.playerLocation.setX(1);
        fixture.velocity = new Vector(0.2D, -0.1D, 0.3D);
        fixture.start();
        fixture.tick(1);

        assertEquals(0.0D, fixture.velocity.getX());
        assertEquals(-0.1D, fixture.velocity.getY());
        assertEquals(0.0D, fixture.velocity.getZ());
        assertTrue(fixture.damage.isEmpty());

        fixture.tick(19);
        assertTrue(fixture.damage.isEmpty());
        fixture.tick(1);
        assertEquals(List.of(15.0D), fixture.damage);
        assertTrue(fixture.pathRequests.isEmpty());

        fixture.playerLocation.setX(8);
        fixture.tick(1);
        assertTrue(fixture.pathRequests.isEmpty());
        finished[0] = true;
        fixture.tick(1);
        fixture.tick(1);
        assertEquals(1, fixture.pathRequests.size());
        assertFalse(cancelled[0]);
    }

    @Test
    void animatedAttackMissesWhenTargetLeavesReachBeforeHit() {
        boolean[] finished = {false};
        Fixture fixture = new Fixture((mobId, targetId) -> new MobMeleeAttackSequence.Attack() {

            @Override
            public int damageDelayTicks() {
                return 5;
            }

            @Override
            public boolean isFinished() {
                return finished[0];
            }

            @Override
            public void cancel() {
            }
        });
        fixture.playerLocation.setX(1);
        fixture.start();
        fixture.tick(1);
        fixture.playerLocation.setX(40);
        assertTrue(fixture.goal.canContinueToUse());
        fixture.tick(5);
        assertTrue(fixture.damage.isEmpty());
        finished[0] = true;
        fixture.tick(1);
        assertFalse(fixture.goal.canContinueToUse());
    }

    @Test
    void interruptedAnimatedAttackCancelsItsAnimation() {
        boolean[] cancelled = {false};
        Fixture fixture = new Fixture((mobId, targetId) -> new MobMeleeAttackSequence.Attack() {

            @Override
            public int damageDelayTicks() {
                return 20;
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public void cancel() {
                cancelled[0] = true;
            }
        });
        fixture.playerLocation.setX(1);
        fixture.start();
        fixture.tick(1);
        fixture.goal.stop();

        assertTrue(cancelled[0]);
        assertTrue(fixture.damage.isEmpty());
    }

    @Test
    void failedAnimatedAttackDoesNotFallBackToImmediateDamage() {
        Fixture fixture = new Fixture((mobId, targetId) -> null);
        fixture.playerLocation.setX(1);
        fixture.start();
        fixture.tick(1);

        assertTrue(fixture.damage.isEmpty());
        assertEquals(0, fixture.swings);
    }

    @Test
    void pursuitSpreadUsesDistinctApproachPointsWithoutDelayingMelee() {
        World world = world();
        Location target = new Location(world, 8, 64, 0);
        Location first = V26_2OwnerMeleeGoal.pursuitLocation(target, new UUID(0, 1), 1.25);
        Location second = V26_2OwnerMeleeGoal.pursuitLocation(target, new UUID(0, 2), 1.25);

        assertEquals(1.25, first.distance(target), 0.00001);
        assertTrue(first.distanceSquared(second) > 0.01);

        Fixture fixture = new Fixture(false, false, false, 1.25);
        fixture.start();
        fixture.tick(1);
        assertEquals(1, fixture.pathRequests.size());
        assertEquals(1.25, fixture.pathRequests.getFirst().distance(fixture.playerLocation), 0.00001);

        fixture.playerLocation.setX(1);
        fixture.tick(60);
        assertEquals(List.of(15.0D, 15.0D), fixture.damage);
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
            case "isChunkLoaded" -> true;
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
        private double mobWidth = 0.6D;

        private Fixture() {
            this(false);
        }

        private Fixture(final MobMeleeAttackSequence sequence) {
            this(false, false, false, 0, 32, sequence);
        }

        private Fixture(final boolean passive, final UUID[] angerTarget) {
            this(false, false, false, 0, 32, null, passive, ignored -> angerTarget[0]);
        }

        private Fixture(final boolean leapEnabled) {
            this(leapEnabled, false);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled) {
            this(leapEnabled, rangedEnabled, false);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled) {
            this(leapEnabled, rangedEnabled, waterEnabled, 0);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled,
                        final double pursuitSpreadRadius) {
            this(leapEnabled, rangedEnabled, waterEnabled, pursuitSpreadRadius, 32);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled,
                        final double pursuitSpreadRadius, final double aggroRadius) {
            this(leapEnabled, rangedEnabled, waterEnabled, pursuitSpreadRadius, aggroRadius, null);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled,
                        final double pursuitSpreadRadius, final double aggroRadius,
                        final MobMeleeAttackSequence sequence) {
            this(leapEnabled, rangedEnabled, waterEnabled, pursuitSpreadRadius, aggroRadius,
                sequence, false, null);
        }

        private Fixture(final boolean leapEnabled, final boolean rangedEnabled, final boolean waterEnabled,
                        final double pursuitSpreadRadius, final double aggroRadius,
                        final MobMeleeAttackSequence sequence, final boolean passive,
                        final Function<UUID, UUID> angerTarget) {
            player = proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "isOnline" -> online;
                case "isDead" -> playerDead;
                case "isInWater" -> false;
                case "getGameMode" -> mode;
                case "getWorld" -> playerLocation.getWorld();
                case "getLocation" -> playerLocation.clone();
                case "getBoundingBox" -> boundingBox(playerLocation, 0.6D, 1.8D);
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
                case "getBoundingBox" -> boundingBox(mobLocation, mobWidth, 1.8D);
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
                new NmsMeleeRangedSpec(rangedEnabled, 60, 40, 0.6, 100), pursuitSpreadRadius, aggroRadius,
                sequence, passive, angerTarget),
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

        private static BoundingBox boundingBox(final Location location, final double width, final double height) {
            double radius = width / 2.0D;
            return new BoundingBox(location.getX() - radius, location.getY(), location.getZ() - radius,
                location.getX() + radius, location.getY() + height, location.getZ() + radius);
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
