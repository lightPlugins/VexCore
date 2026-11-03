package dev.vexsoft.core.api.mob.flight;



/** Immutable displacement or velocity, independent of Bukkit and version-specific entity classes. */
public record FlightVector(double x, double y, double z) {

    public static final FlightVector ZERO = new FlightVector(0, 0, 0);

    /** Validates finite physical values before publishing or applying a flight order. */
    public FlightVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Flight vectors must be finite");
        }
    }

    /** Returns the Euclidean length. */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Returns this vector multiplied by a finite factor. */
    public FlightVector multiply(final double factor) {
        return new FlightVector(x * factor, y * factor, z * factor);
    }

    /** Returns the sum without modifying either input. */
    public FlightVector add(final FlightVector other) {
        return new FlightVector(x + other.x, y + other.y, z + other.z);
    }

    /** Returns the difference without modifying either input. */
    public FlightVector subtract(final FlightVector other) {
        return new FlightVector(x - other.x, y - other.y, z - other.z);
    }
}
