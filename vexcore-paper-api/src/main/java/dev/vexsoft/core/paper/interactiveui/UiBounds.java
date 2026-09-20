package dev.vexsoft.core.paper.interactiveui;

/** Rectangle inside the centered 320 by 180 interactive canvas. */
public record UiBounds(double x, double y, double width, double height) {

    /** Validates finite, positive dimensions within the canvas. */
    public UiBounds {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
            || x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 320 || y + height > 180) {
            throw new IllegalArgumentException("Bounds must fit the 320 by 180 canvas");
        }
    }

    /** Returns whether the point lies inside this rectangle, excluding its right and bottom edges. */
    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
