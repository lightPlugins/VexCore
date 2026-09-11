package dev.vexsoft.core.paper.packets.dummy;

/** Reusable time-based bob and rotation around a model's configured center. */
public record BobRotation(double amplitude, double bobSeconds, double rotationSeconds) {

    /** Validates finite amplitude and positive animation periods. */
    public BobRotation {
        if (!Double.isFinite(amplitude) || amplitude < 0 || !Double.isFinite(bobSeconds) || bobSeconds <= 0
            || !Double.isFinite(rotationSeconds) || rotationSeconds <= 0) {
            throw new IllegalArgumentException("Invalid bob/rotation settings");
        }
    }

    /** Returns the vertical displacement in blocks after the given elapsed seconds. */
    public double height(double elapsedSeconds) {
        return amplitude * Math.sin(2 * Math.PI * (elapsedSeconds % bobSeconds) / bobSeconds);
    }

    /** Returns the rotation in degrees after the given elapsed seconds. */
    public float yaw(double elapsedSeconds) {
        return (float) (360 * (elapsedSeconds % rotationSeconds) / rotationSeconds);
    }
}
