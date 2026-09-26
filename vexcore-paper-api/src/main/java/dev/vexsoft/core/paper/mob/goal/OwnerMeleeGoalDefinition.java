package dev.vexsoft.core.paper.mob.goal;

import dev.vexsoft.core.api.mob.MobMeleeAttackSequence;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Owner-only pursuit and melee combat with bounded path refresh and attack cadence. */
public record OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
                                       int attackIntervalTicks, int pathIntervalTicks,
                                       MeleeLeapDefinition leap, MeleeRangedDefinition ranged,
                                       double pursuitSpreadRadius, double aggroRadius,
                                       MobMeleeAttackSequence attackSequence, boolean passive,
                                       Function<UUID, UUID> angerTarget) implements MobGoalDefinition {

    /** Creates a melee goal with an optional animation and ordinary target acquisition. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap, MeleeRangedDefinition ranged,
        double pursuitSpreadRadius, double aggroRadius, MobMeleeAttackSequence attackSequence) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap, ranged,
            pursuitSpreadRadius, aggroRadius, attackSequence, false, null);
    }

    /** Creates a melee goal with the ordinary immediate attack. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap, MeleeRangedDefinition ranged,
        double pursuitSpreadRadius, double aggroRadius) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap, ranged,
            pursuitSpreadRadius, aggroRadius, null);
    }

    /** Uses the pursuit radius for initial target acquisition. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap, MeleeRangedDefinition ranged,
        double pursuitSpreadRadius) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap, ranged,
            pursuitSpreadRadius, radius);
    }

    /** Creates a melee goal without pursuit spread. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap, MeleeRangedDefinition ranged) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap, ranged, 0);
    }

    /** Creates a melee goal with an optional leap and no ranged fallback. */
    public OwnerMeleeGoalDefinition(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, MeleeLeapDefinition leap) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, leap,
            MeleeRangedDefinition.DISABLED, 0);
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
            || attackIntervalTicks < 1 || pathIntervalTicks < 5 || !Double.isFinite(pursuitSpreadRadius)
            || pursuitSpreadRadius < 0 || pursuitSpreadRadius >= reach || !Double.isFinite(aggroRadius)
            || aggroRadius <= 0 || aggroRadius > radius) {
            throw new IllegalArgumentException("Invalid owner melee goal");
        }
    }
}
