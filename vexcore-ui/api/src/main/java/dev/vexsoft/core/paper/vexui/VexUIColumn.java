package dev.vexsoft.core.paper.vexui;

import java.util.List;
import java.util.Objects;

/** Stacks children vertically; naturally sized children fill the available width. */
public record VexUIColumn(VexUISize size, int gap, List<VexUIElement> children) implements VexUIElement {

    /** Copies the child list and validates the vertical gap. */
    public VexUIColumn {
        Objects.requireNonNull(size, "size");
        children = List.copyOf(children);
        if (gap < 0 || gap > 100) {
            throw new IllegalArgumentException("Invalid column gap");
        }
    }

    /** Creates a content-sized vertical column. */
    public VexUIColumn(final int gap, final VexUIElement... children) {
        this(VexUISize.content(), gap, List.of(children));
    }
}
