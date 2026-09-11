package dev.vexsoft.core.paper.nms.goal;

import java.util.UUID;

/** Version-neutral personal target and bounded melee settings. */
public record NmsOwnerMeleeSpec(
    int priority,
    double speed,
    double radius,
    double reach,
    double damage,
    int attackIntervalTicks,
    int pathIntervalTicks,
    UUID playerId
) { }
