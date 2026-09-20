package dev.vexsoft.core.paper.service.interactiveui;

/** Immutable projection shared by immediate cursor feedback and queued entity-thread hit testing. */
record InteractiveUiCursor(double x, double y, double yaw, double pitch) {

    private static final double PIXELS_PER_DEGREE = 3;

    InteractiveUiCursor rotate(float nextYaw, float nextPitch) {
        double change = (nextYaw - yaw) % 360;

        if (change > 180) {
            change -= 360;
        } else if (change < -180) {
            change += 360;
        }
        return new InteractiveUiCursor(
            Math.clamp(x + change * PIXELS_PER_DEGREE, 0, 319.99),
            Math.clamp(y + (nextPitch - pitch) * PIXELS_PER_DEGREE, 0, 179.99),
            nextYaw,
            nextPitch
        );
    }
}
