package dev.vexsoft.core.api.mob.flight;



/** Optional cylindrical limits for destination flight, measured at the carrier's feet. */
public record FlightBounds(double centerX, double centerZ, double radius, double minY, double maxY) {

    /** Validates finite physical values before publishing or applying a flight order. */
    public FlightBounds {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(radius) || radius <= 0
            || !Double.isFinite(minY) || !Double.isFinite(maxY) || maxY < minY) {
            throw new IllegalArgumentException("Invalid flight bounds");
        }
    }

    /** Returns whether the supplied position lies inside the allowed volume. */
    public boolean contains(final double x, final double y, final double z) {
        double offsetX = x - centerX;
        double offsetZ = z - centerZ;
        return offsetX * offsetX + offsetZ * offsetZ <= radius * radius + 0.000001
            && y >= minY - 0.000001 && y <= maxY + 0.000001;
    }
}
