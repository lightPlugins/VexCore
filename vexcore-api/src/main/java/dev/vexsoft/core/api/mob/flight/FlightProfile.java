package dev.vexsoft.core.api.mob.flight;



/** Shared flight limits in blocks/second, blocks/second squared, degrees/second and blocks. */
public record FlightProfile(
    double maxSpeed,
    double acceleration,
    double deceleration,
    double turnSpeed,
    double arrivalDistance,
    double hoverHeight
) {

    /** Validates finite physical values before publishing or applying a flight order. */
    public FlightProfile {
        if (!positive(maxSpeed) || maxSpeed > 32 || !positive(acceleration) || acceleration > 400
            || !positive(deceleration) || deceleration > 400 || !positive(turnSpeed) || turnSpeed > 1440
            || !positive(arrivalDistance) || arrivalDistance > 1
            || !Double.isFinite(hoverHeight) || hoverHeight < 0 || hoverHeight > 32) {
            throw new IllegalArgumentException("Invalid flight profile");
        }
    }

    /** Returns responsive defaults; zero hover height leaves idle altitude unchanged. */
    public static FlightProfile defaults() {
        return new FlightProfile(8, 40, 50, 540, 0.05, 0);
    }

    private static boolean positive(final double value) {
        return Double.isFinite(value) && value > 0;
    }
}
