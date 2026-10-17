package dev.vexsoft.core.paper.effect;

/** Bounded viewer-local dust ring; optional rising fountains add a fading wake to its expanding edge. */
public record RingBurst(
    double radius,
    double offsetY,
    int frames,
    int points,
    int color,
    float particleSize,
    double fountainHeight,
    int fountains
) {

    public RingBurst {
        if (!Double.isFinite(radius) || radius < 0.1 || radius > 16
            || !Double.isFinite(offsetY) || Math.abs(offsetY) > 16
            || frames < 1 || frames > 80 || points < 8 || points > 128
            || color < 0 || color > 0xFFFFFF || !Float.isFinite(particleSize)
            || particleSize < 0.1F || particleSize > 4F
            || !Double.isFinite(fountainHeight) || fountainHeight < 0 || fountainHeight > 6
            || fountains < 0 || fountains > 32 || (fountains == 0) != (fountainHeight == 0)) {
            throw new IllegalArgumentException("Invalid ring burst geometry or particle settings");
        }
    }

    /** Preserves the flat ring variant without fountains; frames are spaced two ticks apart. */
    public RingBurst(double radius, double offsetY, int frames, int points, int color, float particleSize) {
        this(radius, offsetY, frames, points, color, particleSize, 0, 0);
    }
}
