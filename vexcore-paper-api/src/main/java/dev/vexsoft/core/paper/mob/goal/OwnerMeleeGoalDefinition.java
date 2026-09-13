package dev.vexsoft.core.paper.mob.goal;

import java.util.Objects;

/** Owner-only pursuit and melee combat with bounded path refresh and attack cadence. */
public record OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
                                       int attackIntervalTicks, int pathIntervalTicks,
                                       MeleeLeapDefinition leap, MeleeRangedDefinition ranged) implements MobGoalDefinition {

    /** Creates a melee goal with an optional leap and no ranged fallback. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap,
            MeleeRangedDefinition.DISABLED);
    }

    /** Creates a melee goal without leaping. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, MeleeLeapDefinition.DISABLED);
    }

    /** Validates combat parameters before registration. */
    public OwnerMeleeGoalDefinition {
        Objects.requireNonNull(leap, "leap");
        Objects.requireNonNull(ranged, "ranged");
        if (priority < 0 || !Double.isFinite(speed) || speed <= 0 || !Double.isFinite(radius) || radius <= 0
            || !Double.isFinite(reach) || reach <= 0 || reach > radius || !Double.isFinite(damage) || damage <= 0
            || attackIntervalTicks < 1 || pathIntervalTicks < 5) {
            throw new IllegalArgumentException("Invalid owner melee goal");
        }
    }
}
