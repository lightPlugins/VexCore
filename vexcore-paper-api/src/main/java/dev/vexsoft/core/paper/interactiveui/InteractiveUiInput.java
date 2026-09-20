package dev.vexsoft.core.paper.interactiveui;

import org.jspecify.annotations.Nullable;

/** Pointer event delivered on the viewer's entity scheduler; drag and release retain the pressed target. */
public record InteractiveUiInput(Kind kind, @Nullable String targetId, double x, double y) {

    /** Semantic pointer transitions; CLICK requires press and release over the same element. */
    public enum Kind {
        MOVE, ENTER, LEAVE, PRESS, DRAG, RELEASE, CLICK
    }
}
