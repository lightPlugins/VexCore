package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.position.FlightPositionSafety;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Follows through the carrier's native ground or flying pathfinder, with safe bounded recovery. */
public final class V26_2FollowOwnerGoal extends Goal {

    private final Mob mob;
    private final NmsFollowOwnerSpec settings;
    private final Function<UUID, Player> owners;
    private final Consumer<Player> look;
    private final boolean flying;
    private final BiConsumer<Location, Double> hover;
    private Player owner;
    private int refreshTicks;
    private int stalledTicks;
    private Location previousPosition;
    private Vector followOffset;
    private Location previousOwnerPosition;

    public V26_2FollowOwnerGoal(final Mob mob, final NmsFollowOwnerSpec settings) {
        this(mob, settings, Bukkit::getPlayer, owner -> {
            Location target = owner.getEyeLocation();
            ((CraftMob) mob).getHandle().getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());
        }, isFlying(mob), (target, speed) -> {
            var handle = ((CraftMob) mob).getHandle();
            var flight = flightControl(mob);
            if (flight != null) {
                Player owner = Bukkit.getPlayer(settings.ownerId());
                Vector movement = owner == null ? new Vector() : owner.getVelocity();
                if (owner != null && owner.isOnGround()) {
                    movement.setY(0);
                }
                if (!flight.track(target, new Vec3(
                    movement.getX(), movement.getY(), movement.getZ()), speed)
                    && target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)
                    && (handle.tickCount % settings.pathIntervalTicks() == 0 || !mob.getPathfinder().hasPath())) {
                    mob.getPathfinder().moveTo(target, speed);
                }
            }
        });
    }

    V26_2FollowOwnerGoal(final Mob mob, final NmsFollowOwnerSpec settings,
                       final Function<UUID, Player> owners, final Consumer<Player> look) {
        this(mob, settings, owners, look, false, (target, speed) -> {
        });
    }

    V26_2FollowOwnerGoal(
        final Mob mob,
        final NmsFollowOwnerSpec settings,
        final Function<UUID, Player> owners,
        final Consumer<Player> look,
        final boolean flying,
        final BiConsumer<Location, Double> hover
    ) {
        this.mob = mob;
        this.settings = settings;
        this.owners = owners;
        this.look = look;
        this.flying = flying;
        this.hover = hover;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        owner = owners.apply(settings.ownerId());
        return validOwner() && (flying || distanceSquared() > settings.startDistance() * settings.startDistance());
    }

    @Override
    public boolean canContinueToUse() {
        return validOwner() && (flying || distanceSquared() > settings.stopDistance() * settings.stopDistance());
    }

    @Override
    public void start() {
        refreshTicks = 0;
        stalledTicks = 0;
        previousPosition = mob.getLocation();
    }

    @Override
    public void tick() {
        if (!validOwner()) {
            return;
        }
        look.accept(owner);
        Location target = targetLocation();
        if (flying) {
            hover.accept(target, settings.speed());
        }
        if (refreshTicks-- > 0) {
            return;
        }
        refreshTicks = settings.pathIntervalTicks() - 1;
        Location position = mob.getLocation();
        stalledTicks = (!flying || position.distanceSquared(target) > 0.0625D)
            && previousPosition != null && position.distanceSquared(previousPosition) < 0.04D
            ? stalledTicks + settings.pathIntervalTicks() : 0;
        previousPosition = position;
        if (distanceSquared() >= settings.teleportDistance() * settings.teleportDistance()
            || stalledTicks >= settings.stuckTicks()) {
            recover(target);
            return;
        }
        if (!flying) {
            mob.getPathfinder().moveTo(target, settings.speed());
        }
    }

    @Override
    public void stop() {
        mob.getPathfinder().stopPathfinding();
        var flight = flightControl(mob);
        if (flight != null) {
            flight.stopTracking();
        }
        if (!flying && validOwner() && distanceSquared() <= settings.stopDistance() * settings.stopDistance()) {
            // Retain the existing one-time braking behavior for ground followers.
            mob.setVelocity(mob.getVelocity().multiply(0.5D));
        }
        owner = null;
        previousPosition = null;
        stalledTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean validOwner() {
        return mob.isValid() && owner != null && !NmsMobGoalControl.isPaused(mob) && owner.isOnline() && !owner.isDead()
            && owner.getWorld().equals(mob.getWorld());
    }

    private double distanceSquared() {
        return mob.getLocation().distanceSquared(targetLocation());
    }

    private Location targetLocation() {
        Location target = owner.getLocation();
        if (!flying) {
            return target;
        }
        Vector movement = owner.getVelocity().setY(0);
        if (followOffset == null) {
            // Retain the chosen spawn side until the owner actually changes position.
            followOffset = mob.getLocation().toVector().subtract(target.toVector()).setY(0);
            if (followOffset.lengthSquared() < 0.0001) {
                followOffset = formationOffset(target);
            } else {
                followOffset.normalize().multiply(settings.stopDistance());
            }
        } else if (previousOwnerPosition != null
            && (Math.abs(target.getX() - previousOwnerPosition.getX()) > 0.000001
                || Math.abs(target.getZ() - previousOwnerPosition.getZ()) > 0.000001)) {
            // Looking at the companion must not move it; only walking updates the formation.
            followOffset = formationOffset(target);
        }
        previousOwnerPosition = target.clone();
        Vector prediction = movement.multiply(2);
        double maximumPrediction = settings.stopDistance() / 4;
        if (prediction.lengthSquared() > maximumPrediction * maximumPrediction) {
            // Fast forward motion must not shift the rear-side slot into the owner's view.
            prediction.normalize().multiply(maximumPrediction);
        }
        target.add(followOffset).add(prediction).add(0, settings.flightYOffset(), 0);
        var flight = flightControl(mob);
        if (flight != null) {
            return flight.resolveTrackingTarget(target);
        }
        return target;
    }

    private Vector formationOffset(final Location target) {
        double yaw = Math.toRadians(target.getYaw());
        var right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector relative = mob.getLocation().toVector().subtract(target.toVector()).setY(0);
        double side = relative.dot(right) < -0.1 ? -1 : 1;
        // Sixty degrees from the rear leaves room beside the owner and outside the forward view.
        return right.multiply(side * Math.sqrt(3) / 2)
            .add(new Vector(Math.sin(yaw) / 2, 0, -Math.cos(yaw) / 2))
            .multiply(settings.stopDistance());
    }

    private static boolean isFlying(final Mob mob) {
        var handle = ((CraftMob) mob).getHandle();
        return flightControl(mob) != null || handle.getMoveControl() instanceof FlyingMoveControl<?>
            || handle.getNavigation() instanceof FlyingPathNavigation;
    }

    private static V26_2FlightMoveControl flightControl(final Mob mob) {
        return mob instanceof CraftMob craftMob ? V26_2FlightMoveControl.find(craftMob.getHandle()) : null;
    }

    private void recover(final Location target) {
        // Never force-load chunks or move the attached hologram independently of its carrier.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) < 2 && Math.abs(z) < 2) {
                    continue;
                }
                Location candidate = target.clone().add(x, 0.0D, z);
                candidate.setX(candidate.getBlockX() + 0.5D);
                candidate.setZ(candidate.getBlockZ() + 0.5D);
                if (!candidate.getWorld().isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)) {
                    continue;
                }
                if (!safeRecovery(candidate)) {
                    continue;
                }
                mob.getPathfinder().stopPathfinding();
                if (mob.teleport(candidate)) {
                    var flight = flightControl(mob);
                    if (flight != null) {
                        flight.resetAfterPlacement();
                    } else {
                        mob.setVelocity(new Vector());
                    }
                    stalledTicks = 0;
                    previousPosition = candidate;
                    if (flying) {
                        // Placement invalidates every old order; publish the live owner target again this tick.
                        hover.accept(targetLocation(), settings.speed());
                    }
                    return;
                }
            }
        }
    }

    private boolean safeRecovery(final Location candidate) {
        if (flying) {
            var bounds = mob.getBoundingBox();
            return FlightPositionSafety.isClear(candidate, Math.max(bounds.getWidthX(), bounds.getWidthZ()),
                bounds.getHeight());
        }
        var support = candidate.clone().subtract(0.0D, 1.0D, 0.0D).getBlock();
        return support.getType().isSolid() && !support.getType().name().contains("MAGMA")
            && candidate.getBlock().isPassable() && !candidate.getBlock().isLiquid()
            && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable();
    }
}
