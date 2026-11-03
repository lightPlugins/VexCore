package dev.vexsoft.core.paper.vexui;

import java.util.Objects;

/** Grid child that spans several columns or rows for large and small neighboring cards. */
public record VexUIGridCell(int columns, int rows, VexUIElement content) implements VexUIElement {

    /** Validates bounded spans and the nested content. */
    public VexUIGridCell {
        Objects.requireNonNull(content, "content");
        if (columns < 1 || columns > 12 || rows < 1 || rows > 12) {
            throw new IllegalArgumentException("Grid spans must be between one and twelve");
        }
    }

    @Override
    public VexUISize size() {
        return content.size();
    }
}
