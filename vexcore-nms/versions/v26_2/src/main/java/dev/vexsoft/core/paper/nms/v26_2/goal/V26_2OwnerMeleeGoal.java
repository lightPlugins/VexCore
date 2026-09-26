package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.api.mob.MobMeleeAttackSequence;
import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import java.util.EnumSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Projectile;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.util.Vector;
import org.bukkit.craftbukkit.entity.CraftMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/** Scoped melee AI using native goal scheduling and throttled Paper pathfinding. */
public final class V26_2OwnerMeleeGoal extends Goal {

    private static final int PROGRESS_INTERVAL_TICKS = 40;
    private static final int DIAGNOSTIC_INTERVAL_TICKS = 200;
    private static final double MINIMUM_PROGRESS_SQUARED = 0.04D;
    private static final System.Logger LOGGER = System.getLogger(V26_2OwnerMeleeGoal.class.getName());
    private final Mob mob;
    private final NmsOwnerMeleeSpec spec;
    private final Function<UUID, Player> players;
    private final Consumer<String> diagnostics;
    private final DoubleSupplier movementSpeed;
    private Player target;
    private int pathTicks;
    private int attackTicks;
    private int progressTicks;
    private int diagnosticTicks;
    private Location previousLocation;
    private boolean pathAccepted;
    private boolean pathReachesTarget;
    private int leapCooldown;
    private int leapTicks;
    private boolean leftGround;
    private boolean waterLaunch;
    private boolean leftWater;
    private final Predicate<Vector> leapClearance;
    private final Function<Player, Projectile> projectileFactory;
    private final List<Projectile> projectiles = new ArrayList<>();
    private Location pursuitAnchor;
    private int stalledTicks;
    private int unreachableTicks;
    private int shotCooldown;
    private boolean rangedMode;
    private MobMeleeAttackSequence.Attack activeAttack;
    private int attackElapsedTicks;
    private boolean attackHit;

    public V26_2OwnerMeleeGoal(final Mob mob, final NmsOwnerMeleeSpec specification) {
        this(mob, specification, Bukkit::getPlayer, message -> LOGGER.log(System.Logger.Level.WARNING, message),
            () -> movementSpeed(mob));
    }

    V26_2OwnerMeleeGoal(
        final Mob mob,
        final NmsOwnerMeleeSpec specification,
        final Function<UUID, Player> players,
        final Consumer<String> diagnostics,
        final DoubleSupplier movementSpeed
    ) {
        this(mob, specification, players, diagnostics, movementSpeed, velocity -> clearLeap(mob, velocity));
    }

    V26_2OwnerMeleeGoal(final Mob mob, final NmsOwnerMeleeSpec specification,
        final Function<UUID, Player> players, final Consumer<String> diagnostics,
        final DoubleSupplier movementSpeed, final Predicate<Vector> leapClearance) {
        this(mob, specification, players, diagnostics, movementSpeed, leapClearance,
            player -> V26_2OwnerProjectile.spawn(mob, player, specification));
    }

    V26_2OwnerMeleeGoal(final Mob mob, final NmsOwnerMeleeSpec specification,
        final Function<UUID, Player> players, final Consumer<String> diagnostics,
        final DoubleSupplier movementSpeed, final Predicate<Vector> leapClearance,
        final Function<Player, Projectile> projectileFactory) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.spec = Objects.requireNonNull(specification, "specification");
        this.players = Objects.requireNonNull(players, "players");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.movementSpeed = Objects.requireNonNull(movementSpeed, "movementSpeed");
        this.leapClearance = Objects.requireNonNull(leapClearance, "leapClearance");
        this.projectileFactory = Objects.requireNonNull(projectileFactory, "projectileFactory");

        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player alerted = alertedPlayer();
        if (alerted != null) {
            target = alerted;
            return valid();
        }

        if (spec.passive()) {
            target = null;
            return false;
        }

        target = spec.playerId() == null ? nearestPlayer() : players.apply(spec.playerId());

        return valid() && target.getLocation().distanceSquared(mob.getLocation())
            <= spec.aggroRadius() * spec.aggroRadius();
    }

    private Player alertedPlayer() {
        UUID targetId = spec.angerTarget() == null ? null : spec.angerTarget().apply(mob.getUniqueId());
        if (targetId == null || spec.playerId() != null && !spec.playerId().equals(targetId)) {
            return null;
        }

        Player candidate = players.apply(targetId);
        if (candidate == null || !candidate.isOnline() || candidate.isDead()
            || candidate.getGameMode() == GameMode.CREATIVE || candidate.getGameMode() == GameMode.SPECTATOR
            || !candidate.getWorld().equals(mob.getWorld())
            || candidate.getLocation().distanceSquared(mob.getLocation()) > spec.radius() * spec.radius()) {
            return null;
        }

        return candidate;
    }

    private Player nearestPlayer() {
        Location origin = mob.getLocation();
        double maximumDistance = spec.aggroRadius() * spec.aggroRadius();
        Player nearest = null;

        for (Player candidate : mob.getWorld().getPlayers()) {
            if (!candidate.isOnline() || candidate.isDead() || candidate.getGameMode() == GameMode.CREATIVE
                || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            double distance = candidate.getLocation().distanceSquared(origin);

            if (distance <= maximumDistance) {
                maximumDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    @Override
    public boolean canContinueToUse() {
        if (activeAttack != null) {
            return !NmsMobGoalControl.isPaused(mob) && mob.isValid() && !mob.isDead();
        }

        if (!valid()) {
            return false;
        }

        Player alerted = alertedPlayer();
        return alerted == null ? !spec.passive() : alerted.getUniqueId().equals(target.getUniqueId());
    }

    @Override
    public void start() {
        pathTicks = 0;
        pathAccepted = false;
        resetProgress();

        if (valid()) {
            bindTarget();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (activeAttack == null && !valid()
            || activeAttack != null && (NmsMobGoalControl.isPaused(mob) || !mob.isValid() || mob.isDead())) {
            stop();
            return;
        }

        if (diagnosticTicks > 0) {
            diagnosticTicks--;
        }

        if (attackTicks > 0) {
            attackTicks--;
        }

        if (leapCooldown > 0) {
            leapCooldown--;
        }

        projectiles.removeIf(projectile -> !projectile.isValid());
        if (shotCooldown > 0) {
            shotCooldown--;
        }

        if (activeAttack != null) {
            tickAttack();
            return;
        }

        boolean inLeap = updateLeap();

        boolean canAttack = withinAttackReach() && mob.hasLineOfSight(target);

        if (!inLeap && --pathTicks <= 0) {
            pathTicks = spec.pathIntervalTicks();
            if (bindTarget() && !canAttack) {
                double swimMultiplier = spec.leap().waterEnabled() && mob.isInWater()
                    ? spec.leap().swimSpeedMultiplier() : 1.0D;
                pathAccepted = pursue(spec.speed() * swimMultiplier);
                pathReachesTarget = pathAccepted;
                if (mob instanceof CraftMob craftMob) {
                    var path = craftMob.getHandle().getNavigation().getPath();
                    pathReachesTarget = pathAccepted && path != null && path.canReach();
                }
            }
        }

        // A cancelled target event must not be bypassed by our custom damage calculation.
        if (mob.getTarget() != target) {
            clearProjectiles();
            mob.getPathfinder().stopPathfinding();
            pathAccepted = false;
            monitorProgress("native target was rejected or replaced");
            return;
        }

        if (!canAttack) {
            if (inLeap) {
                resetPursuit();

                resetProgress();
                return;
            }
            boolean fallback = handleRangedFallback();
            if ((!rangedMode && tryLeap()) || fallback) {
                resetProgress();
                return;
            }
            // An accepted path can briefly stall for airborne mobs; keep recovery without a warning.
            monitorProgress(pathAccepted ? null : "path request failed");
            return;
        }

        resetPursuit();
        if (mob.getPathfinder().hasPath()) {
            mob.getPathfinder().stopPathfinding();
        }
        resetProgress();

        if (attackTicks <= 0) {
            attackTicks = spec.attackIntervalTicks();
            if (spec.attackSequence() != null) {
                activeAttack = spec.attackSequence().begin(mob.getUniqueId(), target.getUniqueId());

                if (activeAttack != null) {
                    attackElapsedTicks = 0;
                    attackHit = false;
                    haltAttackMovement();
                    if (activeAttack.damageDelayTicks() == 0) {
                        applyAttackDamage();
                    }
                }
                return;
            }

            mob.swingMainHand();
            target.damage(spec.damage(), mob);
        }
    }

    private void tickAttack() {
        haltAttackMovement();
        attackElapsedTicks++;

        if (!attackHit && attackElapsedTicks >= activeAttack.damageDelayTicks()) {
            applyAttackDamage();
        }

        if (activeAttack.isFinished()) {
            activeAttack = null;
            pathTicks = 0;
        }
    }

    private void applyAttackDamage() {
        attackHit = true;

        if (valid() && mob.getTarget() == target && withinAttackReach() && mob.hasLineOfSight(target)) {
            target.damage(spec.damage(), mob);
        }
    }

    private boolean withinAttackReach() {
        return attackBoxDistanceSquared() <= spec.reach() * spec.reach();
    }

    private double attackBoxDistanceSquared() {
        BoundingBox attacker = mob.getBoundingBox();
        BoundingBox victim = target.getBoundingBox();
        double x = Math.max(0.0D, Math.max(attacker.getMinX() - victim.getMaxX(), victim.getMinX() - attacker.getMaxX()));
        double y = Math.max(0.0D, Math.max(attacker.getMinY() - victim.getMaxY(), victim.getMinY() - attacker.getMaxY()));
        double z = Math.max(0.0D, Math.max(attacker.getMinZ() - victim.getMaxZ(), victim.getMinZ() - attacker.getMaxZ()));
        return x * x + y * y + z * z;
    }

    private void haltAttackMovement() {
        mob.getPathfinder().stopPathfinding();

        if (mob instanceof CraftMob craftMob) {
            var entity = craftMob.getHandle();
            entity.getMoveControl().setWantedPosition(entity.getX(), entity.getY(), entity.getZ(), 0);
        }

        Vector velocity = mob.getVelocity();
        if (velocity.getX() != 0.0D || velocity.getZ() != 0.0D) {
            mob.setVelocity(new Vector(0.0D, velocity.getY(), 0.0D));
        }
    }

    private boolean pursue(final double speed) {
        if (spec.pursuitSpreadRadius() > 0) {
            Location approach = pursuitLocation(target.getLocation(), mob.getUniqueId(), spec.pursuitSpreadRadius());

            if (approach.getWorld().isChunkLoaded(approach.getBlockX() >> 4, approach.getBlockZ() >> 4)
                && mob.getPathfinder().moveTo(approach, speed)) {
                if (!(mob instanceof CraftMob craftMob)) {
                    return true;
                }

                var path = craftMob.getHandle().getNavigation().getPath();

                if (path != null && path.canReach()) {
                    return true;
                }
            }
        }

        return mob.getPathfinder().moveTo(target, speed);
    }

    static Location pursuitLocation(final Location target, final UUID mobId, final double radius) {
        long mixed = mobId.getMostSignificantBits() ^ mobId.getLeastSignificantBits();
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        double angle = (mixed >>> 11) * 0x1.0p-53 * Math.PI * 2;
        return target.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private boolean tryLeap() {
        var leap = spec.leap();
        boolean fromWater = mob.isInWater() && leap.waterEnabled();
        if (target.getLocation().getY() - mob.getLocation().getY() > (fromWater ? 4.0D : 1.0D)) {
            return false;
        }
        if (!leap.enabled() || leapCooldown > 0 || (!fromWater && (!mob.isOnGround() || mob.isInWater()))
            || Math.abs(mob.getVelocity().getY()) > 0.15D || !mob.hasLineOfSight(target)) {
            return false;
        }
        Vector direction = target.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
        double distance = direction.length();
        boolean shoreEscape = fromWater && !target.isInWater();
        if ((!fromWater && distance < 0.001D) || (distance < leap.minDistance() && !shoreEscape)) {
            return false;
        }
        Vector velocity = direction.multiply(
            Math.min(leap.maxHorizontalSpeed(), leap.horizontalSpeed() * distance / leap.minDistance())
                / Math.max(distance, 0.001D));
        velocity.setY(leap.verticalSpeed());
        if (fromWater) {
            velocity.multiply(leap.waterHorizontalMultiplier());
            double horizontal = velocity.clone().setY(0).length();
            if (horizontal > 4.0D) {
                velocity.multiply(4.0D / horizontal);
            }
            velocity.setY(leap.waterVerticalSpeed());
        }
        if (!leapClearance.test(velocity)) {
            return false;
        }
        launch(velocity);
        return true;
    }

    private boolean handleRangedFallback() {
        if (!spec.ranged().enabled()) {
            return false;
        }
        Location current = mob.getLocation();
        boolean moved = pursuitAnchor == null || pursuitAnchor.distanceSquared(current) >= MINIMUM_PROGRESS_SQUARED;
        if (moved) {
            pursuitAnchor = current;
            stalledTicks = 0;
        } else {
            stalledTicks++;
        }
        if (pathReachesTarget && (moved || stalledTicks < spec.ranged().stuckTicks())) {
            unreachableTicks = 0;
            rangedMode = false;
            return true;
        }
        unreachableTicks++;
        if (unreachableTicks >= spec.ranged().stuckTicks() || stalledTicks >= spec.ranged().stuckTicks()) {
            rangedMode = true;
        }
        if (rangedMode && shotCooldown == 0 && projectiles.size() < 16) {
            shotCooldown = spec.ranged().intervalTicks();
            Projectile projectile = projectileFactory.apply(target);
            if (projectile != null) {
                projectiles.add(projectile);
            }
        }
        // Expected unreachable targets use the configured fallback instead of repeated warnings.
        return rangedMode || !pathReachesTarget;
    }

    private void resetPursuit() {
        pursuitAnchor = null;
        stalledTicks = 0;
        unreachableTicks = 0;
        rangedMode = false;
    }

    private void launch(final Vector velocity) {

        mob.getPathfinder().stopPathfinding();
        if (mob instanceof CraftMob craftMob) {
            var entity = craftMob.getHandle();
            entity.getMoveControl().setWantedPosition(entity.getX(), entity.getY(), entity.getZ(), 0);
        }
        mob.setVelocity(velocity);
        leapCooldown = spec.leap().cooldownTicks();
        leapTicks = 1;
        leftGround = false;
        waterLaunch = mob.isInWater();
        leftWater = false;
        pathTicks = 0;
    }

    private boolean updateLeap() {
        if (leapTicks == 0) {
            return false;
        }
        leftGround |= !mob.isOnGround();
        leftWater |= !mob.isInWater();
        if (waterLaunch) {
            if ((leftWater && (mob.isInWater() || mob.isOnGround())) || ++leapTicks > 100
                || (!leftWater && leapTicks > 20)) {
                leapTicks = 0;
                pathTicks = 0;
                return false;
            }
            return true;
        }
        if (mob.isInWater() || (leftGround && mob.isOnGround()) || ++leapTicks > 100
            || (!leftGround && leapTicks > 5)) {
            leapTicks = 0;
            pathTicks = 0;
            return false;
        }
        return true;
    }

    private static boolean clearLeap(final Mob mob, final Vector velocity) {
        var entity = ((CraftMob) mob).getHandle();
        // Check the first impulse's full body clearance, including low ceilings and immediate walls.
        var swept = entity.getBoundingBox().deflate(0.001D)
            .expandTowards(velocity.getX(), velocity.getY(), velocity.getZ());
        return entity.level().noCollision(entity, swept);
    }

    @Override
    public void stop() {
        if (activeAttack != null) {
            activeAttack.cancel();
            activeAttack = null;
        }

        clearProjectiles();
        resetPursuit();

        try {
            mob.getPathfinder().stopPathfinding();
        } finally {
            try {
                if (target != null && mob.getTarget() == target) {
                    mob.setTarget(null);
                }
            } finally {
                target = null;
                previousLocation = null;
                progressTicks = 0;
                pathTicks = 0;
                pathAccepted = false;
                resetPursuit();

            }
        }
    }

    private void clearProjectiles() {
        projectiles.forEach(Projectile::remove);
        projectiles.clear();
    }

    private boolean valid() {
        return !NmsMobGoalControl.isPaused(mob) && target != null && target.isOnline() && !target.isDead()
            && mob.isValid() && !mob.isDead()
            && target.getGameMode() != GameMode.CREATIVE && target.getGameMode() != GameMode.SPECTATOR
            && target.getWorld().equals(mob.getWorld())
            && target.getLocation().distanceSquared(mob.getLocation()) <= spec.radius() * spec.radius();
    }

    private boolean bindTarget() {
        if (mob.getTarget() != target) {
            // Native movement controllers (notably Drowned swimming) also consult the mob target.
            // Keeping only the goal's Player reference does not update that native state.
            mob.setTarget(target);
        }

        return mob.getTarget() == target;
    }

    private void resetProgress() {
        previousLocation = mob.getLocation();
        progressTicks = 0;
    }

    private void monitorProgress(final String reason) {
        Location current = mob.getLocation();

        if (previousLocation == null || previousLocation.getWorld() != current.getWorld()
            || previousLocation.distanceSquared(current) >= MINIMUM_PROGRESS_SQUARED) {
            resetProgress();
            return;
        }

        if (++progressTicks < PROGRESS_INTERVAL_TICKS) {
            return;
        }

        if (reason != null && diagnosticTicks <= 0) {
            diagnosticTicks = DIAGNOSTIC_INTERVAL_TICKS;
            double speed = movementSpeed.getAsDouble();

            diagnostics.accept("Owner melee pursuit stalled: " + reason + "; mob=" + mob.getType()
                + "/" + mob.getUniqueId() + "; owner=" + spec.playerId()
                + "; position=" + current.getX() + "," + current.getY() + "," + current.getZ()
                + "; distance=" + current.distance(target.getLocation())
                + "; hitboxDistance=" + Math.sqrt(attackBoxDistanceSquared()) + "; reach=" + spec.reach()
                + "; movementSpeed=" + (Double.isFinite(speed) ? speed : "unavailable")
                + "; pathAccepted=" + pathAccepted + "; hasPath=" + mob.getPathfinder().hasPath()
                + "; onGround=" + mob.isOnGround() + "; inWater=" + mob.isInWater());
        }

        // Recompute from the actual position after landing or a stalled path. Preserve collisions
        // and normal navigation: an unreachable player must not cause teleports or wall traversal.
        mob.getPathfinder().stopPathfinding();
        pathTicks = 0;
        resetProgress();
    }

    private static double movementSpeed(final Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);

        return speed == null ? Double.NaN : speed.getValue();
    }
}
