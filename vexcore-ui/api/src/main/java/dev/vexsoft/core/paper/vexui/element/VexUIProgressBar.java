package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;

/** Pixel-aligned progress using existing pack geometry; zero segments selects a continuous fill. */
public record VexUIProgressBar(VexUISize size, double progress, int segments, int fill, int track)
    implements VexUIElement {

    /** Clamps finite progress and validates opaque colors and the optional segment count. */
    public VexUIProgressBar {
        Objects.requireNonNull(size, "size");
        if (!Double.isFinite(progress) || segments < 0 || segments > 100 || size.height() < 1
            || (fill | track) >>> 24 != 0) {
            throw new IllegalArgumentException("Invalid progress bar dimensions, progress or colors");
        }
        progress = Math.clamp(progress, 0, 1);
    }

    /** Creates the standard muted-purple progress bar at a logical height of eight pixels. */
    public static VexUIProgressBar of(final double progress) {
        return new VexUIProgressBar(new VexUISize(-1, 8, 0), progress, 0, 0xA58ABA, 0x453C50);
    }
}
