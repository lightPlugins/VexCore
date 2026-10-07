package dev.vexsoft.core.paper.nms.goal;

import java.util.UUID;

/** Version-neutral follow settings with a fixed owner and bounded navigation refresh. */
public record NmsFollowOwnerSpec(
    int priority,
    UUID ownerId,
    double speed,
    double startDistance,
    double stopDistance,
    double teleportDistance,
    int pathIntervalTicks,
    int stuckTicks,
    double flightYOffset
) {

    public NmsFollowOwnerSpec(final int priority, final UUID ownerId, final double speed,
                             final double startDistance, final double stopDistance, final double teleportDistance,
                             final int pathIntervalTicks, final int stuckTicks) {
        this(priority, ownerId, speed, startDistance, stopDistance, teleportDistance, pathIntervalTicks, stuckTicks, 0);
    }
}
