package dev.vexsoft.core.paper.upgrade;

/** One chance-dependent visual pacing profile; zero long-pause bounds disable a dedicated suspense pause. */
public record UpgradeAnimationPacing(
    double minimumSuccessChance, int minSteps, int maxSteps, int minChargeTicks, int maxChargeTicks,
    int minLongPauseTicks, int maxLongPauseTicks
) {

    public UpgradeAnimationPacing {
        if (!Double.isFinite(minimumSuccessChance) || minimumSuccessChance < 0 || minimumSuccessChance > 1
            || minSteps < 2 || maxSteps < minSteps || maxSteps > 16
            || minChargeTicks < 20 || maxChargeTicks < minChargeTicks || maxChargeTicks > 600
            || minChargeTicks < maxSteps * 3
            || minLongPauseTicks < 0 || maxLongPauseTicks < minLongPauseTicks
            || maxLongPauseTicks > 100 || minLongPauseTicks == 0 && maxLongPauseTicks != 0
            || maxLongPauseTicks > 0 && (minSteps < 3
                || minChargeTicks < maxLongPauseTicks + (maxSteps - 1) * 3)) {
            throw new IllegalArgumentException("Invalid upgrade animation pacing profile");
        }
    }
}
