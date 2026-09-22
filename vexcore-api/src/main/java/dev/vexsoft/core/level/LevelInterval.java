package dev.vexsoft.core.level;

/** XP progress within the interval leading into one displayed level. */
public record LevelInterval(double experience, double required, double remaining, double progress) {

    /** Calculates clamped XP progress for the step into the requested level. */
    public static LevelInterval calculate(final CompiledLevelCurve curve, final double experience, final int level) {
        if (!Double.isFinite(experience) || experience < 0) {
            throw new IllegalArgumentException("Invalid experience");
        }
        double target = curve.getRequiredExperience(level);
        double start = level == curve.getMinimumLevel() ? 0 : curve.getRequiredExperience(level - 1);
        double required = target - start;
        double current = Math.clamp(experience - start, 0, required);
        return new LevelInterval(current, required, required - current, required == 0 ? 1 : current / required);
    }
}
