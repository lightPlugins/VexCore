package dev.vexsoft.core.paper.nms.goal;

import java.util.UUID;
import java.util.Objects;

/** Version-neutral personal target and bounded melee settings. */
public record NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
                                int attackIntervalTicks, int pathIntervalTicks, UUID playerId,
                                NmsMeleeLeapSpec leap, NmsMeleeRangedSpec ranged) {

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId, NmsMeleeLeapSpec leap) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId, leap,
            NmsMeleeRangedSpec.DISABLED);
    }

    public NmsOwnerMeleeSpec(int priority, double speed, double radius, double reach, double damage,
        int attackIntervalTicks, int pathIntervalTicks, UUID playerId) {
        this(priority, speed, radius, reach, damage, attackIntervalTicks, pathIntervalTicks, playerId,
            NmsMeleeLeapSpec.DISABLED);
    }

    public NmsOwnerMeleeSpec {
        Objects.requireNonNull(leap, "leap");
        Objects.requireNonNull(ranged, "ranged");
    }

}
