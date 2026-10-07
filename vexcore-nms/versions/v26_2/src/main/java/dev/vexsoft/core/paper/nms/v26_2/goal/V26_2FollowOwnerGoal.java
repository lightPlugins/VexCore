package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.goal.NmsFollowOwnerSpec;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.position.FlightPositionSafety;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.goal.Goal;
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

    public V26_2FollowOwnerGoal(final Mob mob, final NmsFollowOwnerSpec settings) {
        this(mob, settings, Bukkit::getPlayer, owner -> {
            Location target = owner.getEyeLocation();
            ((CraftMob) mob).getHandle().getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());
        }, isFlying(mob), (target, speed) -> {
            var handle = ((CraftMob) mob).getHandle();
            var moved = handle.getBoundingBox().move(target.getX() - handle.getX(),
                target.getY() - handle.getY(), target.getZ() - handle.getZ());
            if (handle.level().noCollision(handle, handle.getBoundingBox().minmax(moved))) {
                mob.getPathfinder().stopPathfinding();
                handle.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
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
        return validOwner() && (distanceSquared() > settings.startDistance() * settings.startDistance()
            || needsHeightCorrection());
    }

    @Override
    public boolean canContinueToUse() {
        return validOwner() && (distanceSquared() > settings.stopDistance() * settings.stopDistance()
            || needsHeightCorrection());
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
        if (flying && horizontalDistanceSquared(mob.getLocation(), target)
            <= settings.stopDistance() * settings.stopDistance()) {
            // Resolve fractional heights directly through native flight controls after arriving near the owner.
            Location verticalTarget = mob.getLocation();
            verticalTarget.setY(target.getY());
            hover.accept(verticalTarget, settings.speed());
        }
        if (refreshTicks-- > 0) {
            return;
        }
        refreshTicks = settings.pathIntervalTicks() - 1;
        Location position = mob.getLocation();
        stalledTicks = previousPosition != null && position.distanceSquared(previousPosition) < 0.04D
            ? stalledTicks + settings.pathIntervalTicks() : 0;
        previousPosition = position;
        if (distanceSquared() >= settings.teleportDistance() * settings.teleportDistance()
            || stalledTicks >= settings.stuckTicks()) {
            recover(target);
            return;
        }
        if (!flying || horizontalDistanceSquared(position, target)
            > settings.stopDistance() * settings.stopDistance()) {
            mob.getPathfinder().moveTo(target, settings.speed());
        }
    }

    @Override
    public void stop() {
        mob.getPathfinder().stopPathfinding();
        if (validOwner() && distanceSquared() <= settings.stopDistance() * settings.stopDistance()) {
            // Flying carriers retain momentum after navigation stops; brake once when the owner is reached.
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
        return owner.getLocation().add(0.0D, flying ? settings.flightYOffset() : 0.0D, 0.0D);
    }

    private boolean needsHeightCorrection() {
        Location position = mob.getLocation();
        Location target = targetLocation();
        return flying && settings.flightYOffset() > 0.0D && Math.abs(position.getY() - target.getY()) > 0.25D
            && horizontalDistanceSquared(position, target) <= settings.startDistance() * settings.startDistance();
    }

    private static double horizontalDistanceSquared(final Location first, final Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return x * x + z * z;
    }

    private static boolean isFlying(final Mob mob) {
        var handle = ((CraftMob) mob).getHandle();
        return handle.getMoveControl() instanceof FlyingMoveControl<?>
            || handle.getMoveControl() instanceof V26_2FlightMoveControl
            || handle.getNavigation() instanceof FlyingPathNavigation;
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
                    mob.setVelocity(new Vector());
                    stalledTicks = 0;
                    previousPosition = candidate;
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
