package dev.vexsoft.core.paper.vexui.element;

import java.util.List;
import java.util.Objects;

/** Places children in equal-width columns with independently measured row heights. */
public record VexUIGrid(VexUISize size, int columns, int gap, List<VexUIElement> children) implements VexUIElement {

    /** Copies the children and validates column count and spacing. */
    public VexUIGrid {
        Objects.requireNonNull(size, "size");
        children = List.copyOf(children);
        if (columns < 1 || columns > 12 || gap < 0 || gap > 100) {
            throw new IllegalArgumentException("Invalid grid columns or gap");
        }
    }
}
