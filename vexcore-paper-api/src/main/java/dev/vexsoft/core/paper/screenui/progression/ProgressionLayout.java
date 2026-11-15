package dev.vexsoft.core.paper.screenui.progression;

/** Shared font and clock encoding for the progression overlay. */
public final class ProgressionLayout {

    // Retire after the shader's exit and before its 120-tick clock can wrap.
    public static final int LIFETIME = 104;
    public static final int EXPERIENCE_LIFETIME = 59;
    public static final int CLOCK_PERIOD = 120;
    public static final int EXPERIENCE_TEXT_PART = 7;
    public static final int EXPERIENCE_BAR_PART = 8;
    public static final int PARTS = 9;

    private ProgressionLayout() {
    }

    /** Returns the font key for the given overlay part. */
    public static String font(int part) {
        return "vexcore:progression/" + part;
    }

    /** Returns the glyph ascent that encodes the overlay part for the shader. */
    public static int ascent(int part) {
        return 8 - (98304 + part * 512 + 128);
    }
}
