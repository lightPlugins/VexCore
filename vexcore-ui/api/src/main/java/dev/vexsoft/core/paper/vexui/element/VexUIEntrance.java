package dev.vexsoft.core.paper.vexui.element;

import net.kyori.adventure.text.format.TextColor;

/** Encodes the client game-time origin of an animated dialog glyph in a near-white Adventure color. */
public final class VexUIEntrance {

    /** Keeps the final pose and the viewport-edge fade on an owned panorama. */
    public static final TextColor SETTLED = TextColor.color(0xFFFFFE);

    /** Native leftward advance for outgoing art. Undo this advance after the glyph or group. */
    public static final int EXIT_DISPLACEMENT = -4096;

    // Two contiguous ranges fit in the low four bits of each RGB channel. This divides the
    // client's 24,000-tick GameTime cycle, including the transition through midnight.
    private static final int PERIOD = 2000;

    private VexUIEntrance() {
    }

    /** Starts a fade and spring at the supplied world game tick, optionally staggered across the scene. */
    public static TextColor color(final long gameTime, final int delayTicks) {
        return encode(gameTime, delayTicks, false);
    }

    /** Reveals and fades an offscreen outgoing glyph at the same clock origin as its replacement. */
    public static TextColor exitColor(final long gameTime, final int delayTicks) {
        return encode(gameTime, delayTicks, true);
    }

    /**
     * Springs a job tab from its previous bounds to the glyph's native bounds. Pass zero width
     * delta for an icon so both of its edges follow the same path and it keeps its aspect ratio.
     *
     * @param startOffsetX previous left edge minus final left edge, in GUI pixels (-64 through 63)
     * @param startWidthDelta previous width minus final width, in GUI pixels (-62, 0 or 62)
     */
    public static TextColor jobTabColor(final long gameTime, final int startOffsetX, final int startWidthDelta) {
        if (startOffsetX < -64 || startOffsetX > 63
            || (startWidthDelta != -62 && startWidthDelta != 0 && startWidthDelta != 62)) {
            throw new IllegalArgumentException("Unsupported job tab displacement");
        }
        int width = 1 + startWidthDelta / 62;
        int payload = (width << 13) | ((startOffsetX + 64) << 6) | Math.floorMod(gameTime, 64);
        // A red byte below 240 distinguishes this band from regular dialog entrance tints.
        return TextColor.color(224 + (payload & 15), 224 + ((payload >> 4) & 31), 208 + (payload >> 9));
    }

    private static TextColor encode(final long gameTime, final int delayTicks, final boolean exiting) {
        int clock = Math.floorMod(Math.floorMod(gameTime, PERIOD) + Math.floorMod(delayTicks, PERIOD), PERIOD);
        int payload = exiting ? PERIOD + clock : clock;
        return TextColor.color(0xF0 | payload >> 8, 0xF0 | (payload >> 4 & 15), 0xF0 | (payload & 15));
    }
}
