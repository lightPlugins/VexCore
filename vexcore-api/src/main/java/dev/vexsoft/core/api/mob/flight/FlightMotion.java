package dev.vexsoft.core.api.mob.flight;



/** The single acceleration and arrival controller used by native and headless flight. */
public final class FlightMotion {

    private FlightMotion() {
    }

    /**
     * Returns displacement for one 20 Hz tick. Previous velocity is the last commanded displacement,
     * before native drag, so Minecraft's drag cannot silently change the configured acceleration.
     * Target velocity is also in blocks/tick; requested speed is in blocks/second and capped by the profile.
     */
    public static FlightVector advance(
        final FlightVector displacement,
        final FlightVector previous,
        final FlightVector targetVelocity,
        final double requestedSpeed,
        final FlightProfile profile
    ) {
        return advance(displacement, previous, targetVelocity, requestedSpeed, profile, true);
    }

    /** Uses the same acceleration without braking at intermediate navigation waypoints. */
    public static FlightVector advance(
        final FlightVector displacement,
        final FlightVector previous,
        final FlightVector targetVelocity,
        final double requestedSpeed,
        final FlightProfile profile,
        final boolean stopAtTarget
    ) {
        if (!Double.isFinite(requestedSpeed) || requestedSpeed < 0) {
            throw new IllegalArgumentException("Flight speed must be finite and non-negative");
        }
        double distance = displacement.length();
        double maximum = Math.min(requestedSpeed, profile.maxSpeed()) / 20;
        double braking = profile.deceleration() / 400;
        double remaining = Math.max(0, distance - profile.arrivalDistance());
        // Solve v + v²/(2b) <= remaining, reserving the current tick before the continuous braking distance.
        double approach = stopAtTarget
            ? Math.min(maximum, Math.sqrt(braking * braking + 2 * braking * remaining) - braking) : maximum;
        FlightVector desired = distance > 0.000001
            ? displacement.multiply(approach / distance).add(targetVelocity) : targetVelocity;
        desired = limit(desired, maximum);
        FlightVector change = desired.subtract(previous);
        double rate = desired.length() < previous.length() ? braking : profile.acceleration() / 400;
        FlightVector next = previous.add(limit(change, rate));
        return next;
    }

    /** Returns whether a stationary target is reached and residual motion has settled. */
    public static boolean arrived(final double distance, final FlightVector velocity, final FlightProfile profile) {
        return distance <= profile.arrivalDistance() + 0.000001 && velocity.length() <= 0.005;
    }

    /** Advances an angle using the shortest turn and the profile's degrees-per-second limit. */
    public static float heading(final float current, final float desired, final FlightProfile profile) {
        double delta = (desired - current) % 360;
        if (delta > 180) {
            delta -= 360;
        } else if (delta < -180) {
            delta += 360;
        }
        return (float) (current + Math.clamp(delta, -profile.turnSpeed() / 20, profile.turnSpeed() / 20));
    }

    private static FlightVector limit(final FlightVector vector, final double maximum) {
        double length = vector.length();
        return length > maximum && length > 0 ? vector.multiply(maximum / length) : vector;
    }
}
