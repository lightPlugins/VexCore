package dev.vexsoft.core.paper.nms.goal;

import dev.vexsoft.core.api.mob.MobMeleeAttackSequence;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Version-neutral scoped target and bounded melee settings. */
public record NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
                                int attackIntervalTicks, int pathIntervalTicks, UUID playerId,
                                NmsMeleeLeapSpec leap, NmsMeleeRangedSpec ranged,
                                double pursuitSpreadRadius, double aggroRadius,
                                MobMeleeAttackSequence attackSequence, boolean passive,
                                Function<UUID, UUID> angerTarget) {

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap,
        NmsMeleeRangedSpec ranged, double pursuitSpreadRadius, double aggroRadius,
        MobMeleeAttackSequence attackSequence) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            leap, ranged, pursuitSpreadRadius, aggroRadius, attackSequence, false, null);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap,
        NmsMeleeRangedSpec ranged, double pursuitSpreadRadius, double aggroRadius) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            leap, ranged, pursuitSpreadRadius, aggroRadius, null);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap,
        NmsMeleeRangedSpec ranged, double pursuitSpreadRadius) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            leap, ranged, pursuitSpreadRadius, radius);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap,
        NmsMeleeRangedSpec ranged) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            leap, ranged, 0);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId, leap,
            NmsMeleeRangedSpec.DISABLED, 0);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            NmsMeleeLeapSpec.DISABLED);
    }

    public NmsOwnerMeleeSpec {
        Objects.requireNonNull(leap, "leap");
        Objects.requireNonNull(ranged, "ranged");
        if (!Double.isFinite(pursuitSpreadRadius) || pursuitSpreadRadius < 0 || pursuitSpreadRadius >= reach
            || !Double.isFinite(aggroRadius) || aggroRadius <= 0 || aggroRadius > radius) {
            throw new IllegalArgumentException("Invalid owner melee radii");
        }
    }

}
