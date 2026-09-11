package dev.vexsoft.core.paper.nms.v26_2.goal;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.paper.nms.goal.NmsLookAtPlayerSpec;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Native selector goal that tracks an eligible nearby player with smooth rotation. */
public final class V26_2LookAtPlayerGoal extends Goal {

    private final Mob handle;
    private final LivingEntity mob;
    private final Pathfinder pathfinder;
    private final NmsLookAtPlayerSpec spec;
    private Player target;
    private int reacquireTicks;
    private int targetLockTicks;

    public V26_2LookAtPlayerGoal(
        final Mob handle,
        final LivingEntity mob,
        final Pathfinder pathfinder,
        final NmsLookAtPlayerSpec spec
    ) {
        this.handle = handle;
        this.mob = mob;
        this.pathfinder = pathfinder;
        this.spec = spec;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!validMobState() || (!spec.whileMoving() && pathfinder.hasPath())) {
            return false;
        }

        if (reacquireTicks-- > 0) {
            return false;
        }

        reacquireTicks = spec.reacquireIntervalTicks();
        target = findTarget(spec.acquireRadius());
        targetLockTicks = spec.minimumTargetLockTicks();

        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return validMobState() && validTarget(target, spec.releaseRadius()) && (spec.whileMoving()
            || !pathfinder.hasPath());
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }

        if (targetLockTicks > 0) {
            targetLockTicks--;
        } else if (reacquireTicks-- <= 0) {
            reacquireTicks = spec.reacquireIntervalTicks();
            Player replacement = findTarget(spec.acquireRadius());

            if (replacement != null && shouldSwitch(replacement)) {
                target = replacement;
                targetLockTicks = spec.minimumTargetLockTicks();
            }
        }

        rotateTowards(target);
    }

    @Override
    public void stop() {
        target = null;
        reacquireTicks = spec.reacquireIntervalTicks();
        targetLockTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private Player findTarget(final double radius) {
        UUID personal = spec.personalPlayerId();

        if (personal != null) {
            Player player = Bukkit.getPlayer(personal);

            return validTarget(player, radius) ? player : null;
        }

        return mob.getWorld()
            .getNearbyPlayers(
                mob.getLocation(),
                radius
            )
            .stream()
            .filter(player -> validTarget(player, radius))
            .min(Comparator.comparingDouble(player -> distanceSquared(player, mob)))
            .orElse(null);
    }

    private boolean shouldSwitch(final Player replacement) {
        if (target == null || replacement.getUniqueId().equals(target.getUniqueId())) {
            return target == null;
        }

        double currentDistance = Math.sqrt(distanceSquared(target, mob));
        double replacementDistance = Math.sqrt(distanceSquared(replacement, mob));

        return replacementDistance + spec.switchDistanceAdvantage() < currentDistance;
    }

    private boolean validMobState() {
        return mob.isValid() && !mob.isDead();
    }

    private boolean validTarget(final Player player, final double radius) {
        if (player == null || !player.isOnline() || player.isDead() || player.getWorld() != mob.getWorld()
            || player.getGameMode() == GameMode.SPECTATOR || distanceSquared(player, mob) > radius * radius) {
            return false;
        }

        return !spec.requireLineOfSight() || mob.hasLineOfSight(player);
    }

    private void rotateTowards(final Player player) {
        Location source = mob.getLocation();
        Location destination = player.getEyeLocation();
        double deltaX = destination.getX() - source.getX();
        double deltaY = destination.getY() - (source.getY() + mob.getHeight() * 0.85D);
        double deltaZ = destination.getZ() - source.getZ();
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontal < 0.0001D) {
            return;
        }

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float desiredPitch = spec.yawOnly() ? 0.0F : (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        float maximumStep = (float) (spec.rotationSpeed() / 20.0D);
        float yaw = Mth.rotateIfNecessary(handle.getYRot(), desiredYaw, maximumStep);
        float pitch = Mth.rotateIfNecessary(handle.getXRot(), desiredPitch, maximumStep);

        handle.setYRot(yaw);
        handle.setYHeadRot(yaw);
        handle.setYBodyRot(yaw);
        handle.setXRot(pitch);
    }

    private static double distanceSquared(final Player player, final LivingEntity mob) {
        return player.getLocation().distanceSquared(mob.getLocation());
    }
}
