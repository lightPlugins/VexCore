package dev.vexsoft.core.paper.nms.v26_2.goal;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.vexsoft.core.paper.nms.goal.NmsRandomMovementSpec;
import dev.vexsoft.core.paper.nms.position.GroundPositionSafety;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Native selector goal with cave-safe candidate and path validation. */
public final class V26_2RandomMovementGoal extends Goal {

  private static final int MAX_STUCK_TICKS = 40;
  private static final double MINIMUM_PROGRESS_SQUARED = 0.01D;
  private final Mob handle;
  private final LivingEntity mob;
  private final Pathfinder pathfinder;
  private final NmsRandomMovementSpec spec;
  private final Location origin;
  private Pathfinder.PathResult pendingPath;
  private int cooldownTicks;
  private int stuckTicks;
  private Location previousLocation;

  public V26_2RandomMovementGoal(
      final Mob handle,
      final LivingEntity mob,
      final Pathfinder pathfinder,
      final NmsRandomMovementSpec spec
  ) {
    this.handle = handle;
    this.mob = mob;
    this.pathfinder = pathfinder;
    this.spec = spec;
    this.origin = spec.origin();
    cooldownTicks = randomIdleTicks();
    setFlags(EnumSet.of(Flag.MOVE));
  }

  @Override
  public boolean canUse() {
    if (!mob.isValid() || mob.isDead() || pathfinder.hasPath()) {
      return false;
    }
    if (cooldownTicks-- > 0) {
      return false;
    }
    pendingPath = findTargetPath();
    if (pendingPath == null) {
      cooldownTicks = spec.retryDelayTicks();
      return false;
    }
    return true;
  }

  @Override
  public boolean canContinueToUse() {
    return mob.isValid() && !mob.isDead() && pathfinder.hasPath()
        && horizontalDistanceSquared(mob.getLocation(), origin)
        <= spec.leashRadius() * spec.leashRadius() * 1.10D;
  }

  @Override
  public void start() {
    if (pendingPath == null || !pathfinder.moveTo(pendingPath, spec.speed())) {
      cooldownTicks = spec.retryDelayTicks();
    }
    pendingPath = null;
    previousLocation = mob.getLocation();
    stuckTicks = 0;
  }

  @Override
  public void tick() {
    Location current = mob.getLocation();
    if (previousLocation != null && previousLocation.getWorld() == current.getWorld()
        && previousLocation.distanceSquared(current) <= MINIMUM_PROGRESS_SQUARED) {
      stuckTicks++;
      if (stuckTicks >= MAX_STUCK_TICKS) {
        pathfinder.stopPathfinding();
        cooldownTicks = spec.retryDelayTicks();
      }
    } else {
      stuckTicks = 0;
      previousLocation = current;
    }
  }

  @Override
  public void stop() {
    pendingPath = null;
    previousLocation = null;
    stuckTicks = 0;
    cooldownTicks = randomIdleTicks();
  }

  @Override
  public boolean requiresUpdateEveryTick() {
    return true;
  }

  private Pathfinder.PathResult findTargetPath() {
    World world = mob.getWorld();
    Location current = mob.getLocation();
    for (int attempt = 0; attempt < spec.attempts(); attempt++) {
      double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
      double distance = ThreadLocalRandom.current().nextDouble(
          spec.minimumDistance(),
          spec.maximumDistance()
      );
      double x = current.getX() + Math.cos(angle) * distance;
      double z = current.getZ() + Math.sin(angle) * distance;
      double fromOrigin = Math.sqrt(horizontalDistanceSquared(origin, x, z));
      if (fromOrigin > spec.leashRadius()) {
        double ratio = spec.leashRadius() * 0.90D / fromOrigin;
        x = origin.getX() + (x - origin.getX()) * ratio;
        z = origin.getZ() + (z - origin.getZ()) * ratio;
      }
      int blockX = (int) Math.floor(x);
      int blockZ = (int) Math.floor(z);
      if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
        continue;
      }
      Location candidate = findGround(world, x, z, current.getBlockY());
      if (candidate == null || candidate.distanceSquared(current) < 1.0D) {
        continue;
      }
      Pathfinder.PathResult path = pathfinder.findPath(candidate);
      if (validPath(path)) {
        return path;
      }
    }
    return null;
  }

  private Location findGround(
      final World world,
      final double x,
      final double z,
      final int preferredY
  ) {
    int minimumY = Math.max(world.getMinHeight() + 1, origin.getBlockY() + spec.minimumYOffset());
    int maximumY = Math.min(world.getMaxHeight() - 2, origin.getBlockY() + spec.maximumYOffset());
    int clampedPreferred = Math.clamp(preferredY, minimumY, maximumY);
    for (int offset = 0; offset <= maximumY - minimumY; offset++) {
      int upper = clampedPreferred + offset;
      if (upper <= maximumY && validStandLocation(world, x, upper, z)) {
        return centered(world, x, upper, z);
      }
      int lower = clampedPreferred - offset;
      if (offset > 0 && lower >= minimumY && validStandLocation(world, x, lower, z)) {
        return centered(world, x, lower, z);
      }
    }
    return null;
  }

  private boolean validStandLocation(
      final World world,
      final double x,
      final int y,
      final double z
  ) {
    int blockX = (int) Math.floor(x);
    int blockZ = (int) Math.floor(z);
    if (!GroundPositionSafety.isSafe(
        world, blockX, y, blockZ, 2, spec.avoidFluids(), spec.avoidHazards(),
        spec.allowedSupportBlocks(), spec.deniedSupportBlocks()
    )) {
      return false;
    }
    Location target = centered(world, x, y, z);
    Location current = mob.getLocation();
    return handle.level().noCollision(handle, handle.getBoundingBox().move(
        target.getX() - current.getX(),
        target.getY() - current.getY(),
        target.getZ() - current.getZ()
    ));
  }

  private boolean validPath(final Pathfinder.PathResult path) {
    if (path == null || !path.canReachFinalPoint() || path.getFinalPoint() == null) {
      return false;
    }
    if (!spec.constrainEntirePath()) {
      return true;
    }
    int minimumY = origin.getBlockY() + spec.minimumYOffset();
    int maximumY = origin.getBlockY() + spec.maximumYOffset();
    for (Location point : path.getPoints()) {
      if (point.getBlockY() < minimumY || point.getBlockY() > maximumY) {
        return false;
      }
      Material support = point.getBlock().getRelative(0, -1, 0).getType();
      if (spec.deniedSupportBlocks().contains(support)
          || (!spec.allowedSupportBlocks().isEmpty()
          && !spec.allowedSupportBlocks().contains(support))) {
        return false;
      }
    }
    return true;
  }

  private int randomIdleTicks() {
    return ThreadLocalRandom.current().nextInt(
        spec.minimumIdleTicks(),
        spec.maximumIdleTicks() + 1
    );
  }

  private static Location centered(
      final World world,
      final double x,
      final int y,
      final double z
  ) {
    return new Location(world, Math.floor(x) + 0.5D, y, Math.floor(z) + 0.5D);
  }

  private static double horizontalDistanceSquared(final Location first, final Location second) {
    return horizontalDistanceSquared(first, second.getX(), second.getZ());
  }

  private static double horizontalDistanceSquared(
      final Location first,
      final double x,
      final double z
  ) {
    double deltaX = first.getX() - x;
    double deltaZ = first.getZ() - z;
    return deltaX * deltaX + deltaZ * deltaZ;
  }
}
