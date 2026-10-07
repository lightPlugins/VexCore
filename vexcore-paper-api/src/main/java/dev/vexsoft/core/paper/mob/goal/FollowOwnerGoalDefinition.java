package dev.vexsoft.core.paper.mob.goal;

import java.util.Objects;
import java.util.UUID;

/** Follows an explicit owner independently of the mob's viewer scope. */
public record FollowOwnerGoalDefinition(
    int priority,
    UUID ownerId,
    double speed,
    double startDistance,
    double stopDistance,
    double teleportDistance,
    int pathIntervalTicks,
    int stuckTicks,
    double flightYOffset
) implements MobGoalDefinition {

    /** Creates a follower with native flight height behavior. */
    public FollowOwnerGoalDefinition(
        final int priority,
        final UUID ownerId,
        final double speed,
        final double startDistance,
        final double stopDistance,
        final double teleportDistance,
        final int pathIntervalTicks,
        final int stuckTicks
    ) {
        this(priority, ownerId, speed, startDistance, stopDistance, teleportDistance, pathIntervalTicks, stuckTicks, 0);
    }

    /** Validates pursuit thresholds and the optional feet-relative flight height. */
    public FollowOwnerGoalDefinition {
        Objects.requireNonNull(ownerId, "ownerId");
        if (priority < 0 || !Double.isFinite(speed) || speed <= 0.0D
            || !Double.isFinite(startDistance) || !Double.isFinite(stopDistance)
            || !Double.isFinite(teleportDistance) || stopDistance <= 0.0D
            || startDistance <= stopDistance || teleportDistance <= startDistance
            || pathIntervalTicks < 5 || stuckTicks < pathIntervalTicks
            || !Double.isFinite(flightYOffset) || flightYOffset < 0.0D || flightYOffset > 32.0D) {
            throw new IllegalArgumentException("Invalid follow owner goal");
        }
    }
}
