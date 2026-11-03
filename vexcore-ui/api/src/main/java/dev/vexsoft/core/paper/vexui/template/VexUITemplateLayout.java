package dev.vexsoft.core.paper.vexui.template;

/** Bounded logical canvas and fixed page geometry; partial pages always reserve the same rows. */
public record VexUITemplateLayout(int width, int columns, int rows, int cellHeight, int gap) {

    /** Validates the visible canvas budget before a template is opened. */
    public VexUITemplateLayout {
        if (width < 320 || width > 700 || columns < 1 || columns > 8 || rows < 1 || rows > 8
            || cellHeight < 16 || cellHeight > 180 || gap < 0 || gap > 16
            || rows * cellHeight + (rows - 1) * gap > 650) {
            throw new IllegalArgumentException("Invalid VexUI template geometry");
        }
    }

    /** Returns the number of single-cell entries reserved on each page. */
    public int capacity() {
        return columns * rows;
    }

    /** Returns the standard six-card profession or navigation overview. */
    public static VexUITemplateLayout overview() {
        return new VexUITemplateLayout(480, 3, 2, 68, 4);
    }
}
