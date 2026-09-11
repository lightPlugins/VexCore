package dev.vexsoft.core.paper.mob.goal;

/** Owner-only pursuit and melee combat with bounded path refresh and attack cadence. */
public record OwnerMeleeGoalDefinition(
    int priority,
    double speed,
    double radius,
    double reach,
    double damage,
    int attackIntervalTicks,
    int pathIntervalTicks
)
    implements MobGoalDefinition {

  /** Validates combat parameters before registration. */
  public OwnerMeleeGoalDefinition {
    if (priority < 0
        || !Double.isFinite(speed)
        || speed <= 0
        || !Double.isFinite(radius)
        || radius <= 0
        || !Double.isFinite(reach)
        || reach <= 0
        || reach > radius
        || !Double.isFinite(damage)
        || damage <= 0
        || attackIntervalTicks < 1
        || pathIntervalTicks < 5) {
      throw new IllegalArgumentException("Invalid owner melee goal");
    }
  }
}
