package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;

/** Reserves empty space without adding an action or a texture. */
public record VexUISpacer(VexUISize size) implements VexUIElement {

    /** Requires an explicit sizing policy for this empty region. */
    public VexUISpacer {
        Objects.requireNonNull(size, "size");
    }
}
