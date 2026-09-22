package dev.vexsoft.core.paper.screenui;

import java.util.Map;

/** Logical unscaled coordinates after wrapping; slots include absolute anchor-relative positions. */
public record UiPanelBounds(int x, int y, int width, int height, Map<String, Rect> slots) {

    /** Copies the node-coordinate map before exposing it to callers. */
    public UiPanelBounds {
        slots = Map.copyOf(slots);
    }

    /** One node rectangle in the panel anchor coordinate system. */
    public record Rect(int x, int y, int width, int height) {

    }
}
