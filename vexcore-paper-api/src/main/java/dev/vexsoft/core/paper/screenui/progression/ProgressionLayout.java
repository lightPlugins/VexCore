package dev.vexsoft.core.paper.screenui.progression;

/** Shared font and clock encoding for the 26.2 progression overlay. */
public final class ProgressionLayout {

    // Retire after the shader's exit and before its 120-tick clock can wrap.
    public static final int LIFETIME = 104;
    public static final int CLOCK_PERIOD = 120;
    public static final int PARTS = 7;

    private ProgressionLayout() {
    }

    public static String font(int part) {
        return "vexcore:progression/" + part;
    }

    public static int ascent(int part) {
        return 8 - (98304 + part * 512 + 128);
    }
}
