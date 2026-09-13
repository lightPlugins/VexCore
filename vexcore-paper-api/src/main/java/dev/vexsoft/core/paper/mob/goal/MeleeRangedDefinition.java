package dev.vexsoft.core.paper.mob.goal;

/** Bounded wall-piercing Shulker fallback for an unreachable owner. */
public record MeleeRangedDefinition(boolean enabled, int stuckTicks, int intervalTicks,
    double speed, int lifetimeTicks) {

    public static final MeleeRangedDefinition DISABLED = new MeleeRangedDefinition(false, 60, 40, 0.6, 100);

    /** Validates timing and projectile speed in blocks per tick. */
    public MeleeRangedDefinition {
        if (stuckTicks < 1 || intervalTicks < 1 || !Double.isFinite(speed) || speed <= 0 || speed > 4
            || lifetimeTicks < 1 || lifetimeTicks > 1200) {
            throw new IllegalArgumentException("Invalid unreachable melee attack settings");
        }
    }
}
