package dev.vexsoft.core.paper.vexui;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Resolved localized text, clipped to its box while retaining component styles. */
public record VexUIText(VexUISize size, Component text, boolean centered) implements VexUIElement {

    /** Requires a resolved component and its sizing policy. */
    public VexUIText {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(text, "text");
    }

    /** Creates left-aligned text with natural dimensions. */
    public VexUIText(final Component text) {
        this(VexUISize.content(), text, false);
    }
}
