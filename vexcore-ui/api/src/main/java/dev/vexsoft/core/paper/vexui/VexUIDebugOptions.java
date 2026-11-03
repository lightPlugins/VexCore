package dev.vexsoft.core.paper.vexui;

/** Opt-in developer overlays; disabled during normal presentation. */
public record VexUIDebugOptions(boolean bounds, boolean actions) {

    /** Returns normal presentation without overlays or implementation details. */
    public static VexUIDebugOptions disabled() {
        return new VexUIDebugOptions(false, false);
    }

    /** Shows box boundaries and action identifiers in developer tooltips. */
    public static VexUIDebugOptions inspect() {
        return new VexUIDebugOptions(true, true);
    }
}
