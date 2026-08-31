package dev.vexsoft.core.paper.mob.goal;

/** Scope-aware smooth player tracking for a custom mob. */
public record LookAtPlayerGoalDefinition(
    int priority,
    double acquireRadius,
    double releaseRadius,
    int reacquireIntervalTicks,
    int minimumTargetLockTicks,
    double switchDistanceAdvantage,
    boolean requireLineOfSight,
    boolean whileMoving,
    boolean yawOnly
) implements MobGoalDefinition {

  /** Creates and validates a look-at-player goal. */
  public LookAtPlayerGoalDefinition {
    if (priority < 0 || !Double.isFinite(acquireRadius) || acquireRadius <= 0.0D
        || !Double.isFinite(releaseRadius) || releaseRadius < acquireRadius
        || reacquireIntervalTicks < 1 || minimumTargetLockTicks < 0
        || !Double.isFinite(switchDistanceAdvantage) || switchDistanceAdvantage < 0.0D) {
      throw new IllegalArgumentException("Invalid look-at-player goal settings");
    }
  }

  /** Creates a builder with stable target-selection defaults. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for a look-at-player goal. */
  public static final class Builder {

    private int priority = 20;
    private double acquireRadius = 6.0D;
    private double releaseRadius = 8.0D;
    private int reacquireIntervalTicks = 10;
    private int minimumTargetLockTicks = 40;
    private double switchDistanceAdvantage = 1.5D;
    private boolean requireLineOfSight = true;
    private boolean whileMoving = true;
    private boolean yawOnly = true;

    private Builder() { }

    /** Sets the goal priority. */
    public Builder priority(final int value) {
      priority = value;
      return this;
    }

    /** Sets target acquisition and release radii. */
    public Builder radii(final double acquire, final double release) {
      acquireRadius = acquire;
      releaseRadius = release;
      return this;
    }

    /** Sets how frequently a new target may be searched. */
    public Builder reacquireIntervalTicks(final int value) {
      reacquireIntervalTicks = value;
      return this;
    }

    /** Sets the minimum time for which a valid current target is retained. */
    public Builder minimumTargetLockTicks(final int value) {
      minimumTargetLockTicks = value;
      return this;
    }

    /** Sets how much nearer a replacement target must be. */
    public Builder switchDistanceAdvantage(final double value) {
      switchDistanceAdvantage = value;
      return this;
    }

    /** Sets whether the mob must have direct sight of its target. */
    public Builder requireLineOfSight(final boolean value) {
      requireLineOfSight = value;
      return this;
    }

    /** Sets whether looking remains active while a navigation path is running. */
    public Builder whileMoving(final boolean value) {
      whileMoving = value;
      return this;
    }

    /** Sets whether vertical pitch is ignored. */
    public Builder yawOnly(final boolean value) {
      yawOnly = value;
      return this;
    }

    /** Creates the validated goal definition. */
    public LookAtPlayerGoalDefinition build() {
      return new LookAtPlayerGoalDefinition(
          priority, acquireRadius, releaseRadius, reacquireIntervalTicks,
          minimumTargetLockTicks, switchDistanceAdvantage, requireLineOfSight,
          whileMoving, yawOnly
      );
    }
  }
}
