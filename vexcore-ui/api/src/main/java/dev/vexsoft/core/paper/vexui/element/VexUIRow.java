package dev.vexsoft.core.paper.vexui.element;

import java.util.List;
import java.util.Objects;

/** Arranges children horizontally, distributing remaining width among weighted children. */
public record VexUIRow(VexUISize size, int gap, List<VexUIElement> children) implements VexUIElement {

    /** Copies the child list and validates horizontal spacing. */
    public VexUIRow {
        Objects.requireNonNull(size, "size");
        children = List.copyOf(children);
        if (gap < 0 || gap > 100) {
            throw new IllegalArgumentException("Invalid row gap");
        }
    }

    /** Creates a content-sized horizontal row. */
    public VexUIRow(final int gap, final VexUIElement... children) {
        this(VexUISize.content(), gap, List.of(children));
    }
}
