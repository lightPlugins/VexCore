package dev.vexsoft.core.paper.vexui.layout;

/** Logical pixel rectangle before Minecraft applies the viewer's GUI scale. */
public record VexUIBounds(int x, int y, int width, int height) {

    public VexUIBounds {
        if (x < 0 || y < 0 || width < 0 || height < 0 || x + width > 960 || y + height > 900) {
            throw new IllegalArgumentException("VexUI layout exceeds its supported canvas");
        }
    }
}
