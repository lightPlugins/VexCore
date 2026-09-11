package dev.vexsoft.core.paper.nms.goal;

import java.util.UUID;

/** Version-neutral data required by the native look-at-player goal. */
public record NmsLookAtPlayerSpec(int priority, double acquireRadius, double releaseRadius, int reacquireIntervalTicks,
                                  int minimumTargetLockTicks, double switchDistanceAdvantage,
                                  boolean requireLineOfSight, boolean whileMoving, boolean yawOnly,
                                  double rotationSpeed, UUID personalPlayerId) {

}
