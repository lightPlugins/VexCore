package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Styled wrapping text; maximumLines bounds card previews while detail views can paginate complete text. */
public record VexUIParagraph(VexUISize size, Component text, int maximumLines) implements VexUIElement {

    /** Validates wrapping limits; zero permits all measured lines. */
    public VexUIParagraph {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(text, "text");
        if (maximumLines < 0 || maximumLines > 100) {
            throw new IllegalArgumentException("Invalid paragraph line limit");
        }
    }

    /** Creates an unrestricted content-sized paragraph. */
    public VexUIParagraph(final Component text) {
        this(VexUISize.content(), text, 0);
    }
}
