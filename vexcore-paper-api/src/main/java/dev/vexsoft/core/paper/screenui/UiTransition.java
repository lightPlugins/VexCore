package dev.vexsoft.core.paper.screenui;

/**
 * Short client-frame transition for anchored text. Replaces neither the screen nor its element ID.
 * Use world game time and replace with a static layout after completion (at latest within 250 ticks).
 * The transport quantizes tint to three bits per RGB channel, like toast animations.
 */
public record UiTransition(Kind kind, int startTick, int durationTicks, int offsetX) {

    /** Validates bounded motion and the four shader duration presets. */
    public UiTransition {
        if (kind == null || startTick < 0 || startTick >= 500 || offsetX < -32 || offsetX > 31
            || durationTicks != 2 && durationTicks != 4 && durationTicks != 8 && durationTicks != 16
            || kind == Kind.FADE_OUT && offsetX != 0) {
            throw new IllegalArgumentException("Invalid UI transition");
        }
    }

    /** Moves from the supplied horizontal offset to the layout's destination in logical GUI pixels. */
    public static UiTransition move(long gameTime, int ticks, int offsetX) {
        return new UiTransition(Kind.MOVE_X, (int) Math.floorMod(gameTime, 500), ticks, offsetX);
    }

    /** Fades an overlay out, revealing the already-updated element beneath it. */
    public static UiTransition fadeOut(long gameTime, int ticks) {
        return new UiTransition(Kind.FADE_OUT, (int) Math.floorMod(gameTime, 500), ticks, 0);
    }

    /** Returns the duration's compact protocol index. */
    public int durationIndex() {
        return Integer.numberOfTrailingZeros(durationTicks) - 1;
    }

    /** Supported short transitions; no scheduler is created by either mode. */
    public enum Kind { MOVE_X, FADE_OUT }
}
