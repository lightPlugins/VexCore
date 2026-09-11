package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.goal.NmsOwnerMeleeSpec;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Personal melee AI using native goal scheduling and throttled Paper pathfinding. */
public final class V26_2OwnerMeleeGoal extends Goal {
  private final Mob mob;
  private final NmsOwnerMeleeSpec spec;
  private Player target;
  private int pathTicks;
  private int attackTicks;

  public V26_2OwnerMeleeGoal(final Mob mob, final NmsOwnerMeleeSpec specification) {
    this.mob = mob;
    this.spec = specification;
    setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    target = spec.playerId() == null ? null : Bukkit.getPlayer(spec.playerId());
    return valid();
  }

  @Override
  public boolean canContinueToUse() {
    return valid();
  }

  private boolean valid() {
    return target != null && target.isOnline() && !target.isDead() && mob.isValid()
        && target.getGameMode() != GameMode.CREATIVE && target.getGameMode() != GameMode.SPECTATOR
        && target.getWorld().equals(mob.getWorld())
        && target.getLocation().distanceSquared(mob.getLocation()) <= spec.radius() * spec.radius();
  }

  @Override
  public boolean requiresUpdateEveryTick() {
    return true;
  }

  @Override
  public void tick() {
    if (!valid()) {
      return;
    }
    if (--pathTicks <= 0) {
      pathTicks = spec.pathIntervalTicks();
      mob.getPathfinder().moveTo(target, spec.speed());
    }
    if (--attackTicks <= 0
        && mob.getLocation().distanceSquared(target.getLocation()) <= spec.reach() * spec.reach()
        && mob.hasLineOfSight(target)) {
      attackTicks = spec.attackIntervalTicks();
      mob.swingMainHand();
      target.damage(spec.damage(), mob);
    }
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
    target = null;
    pathTicks = 0;
  }
}
