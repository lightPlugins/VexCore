package dev.vexsoft.core.paper.nms.goal;

/** Optional bounded velocity leap for aggressive owner melee pursuit. */
public record NmsMeleeLeapSpec(boolean enabled, double minDistance, int cooldownTicks,
    double horizontalSpeed, double maxHorizontalSpeed, double verticalSpeed,
    boolean waterEnabled, double waterVerticalSpeed, double waterHorizontalMultiplier, double swimSpeedMultiplier) {

    /** Creates leap settings without a water launch override. */
    public NmsMeleeLeapSpec(boolean enabled, double minDistance, int cooldownTicks,
        double horizontalSpeed, double maxHorizontalSpeed, double verticalSpeed) {
        this(enabled, minDistance, cooldownTicks, horizontalSpeed, maxHorizontalSpeed, verticalSpeed,
            false, 1.2, 1.4, 1.5);
    }

    public static final NmsMeleeLeapSpec DISABLED = new NmsMeleeLeapSpec(false, 6, 60, 0.8, 1.3, 0.45);

    public NmsMeleeLeapSpec {
        if (!Double.isFinite(minDistance) || minDistance <= 0 || cooldownTicks < 1
            || !Double.isFinite(horizontalSpeed) || horizontalSpeed <= 0
            || !Double.isFinite(maxHorizontalSpeed) || maxHorizontalSpeed < horizontalSpeed
            || maxHorizontalSpeed > 4 || !Double.isFinite(verticalSpeed) || verticalSpeed <= 0 || verticalSpeed > 4
            || !Double.isFinite(waterVerticalSpeed) || waterVerticalSpeed <= 0 || waterVerticalSpeed > 4
            || !Double.isFinite(waterHorizontalMultiplier) || waterHorizontalMultiplier < 1 || waterHorizontalMultiplier > 4
            || !Double.isFinite(swimSpeedMultiplier) || swimSpeedMultiplier < 1 || swimSpeedMultiplier > 4) {
            throw new IllegalArgumentException("Invalid melee leap settings");
        }
    }
}
