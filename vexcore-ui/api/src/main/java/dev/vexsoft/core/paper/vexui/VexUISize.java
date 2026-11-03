package dev.vexsoft.core.paper.vexui;

/** Preferred logical pixel dimensions; -1 means content-sized and weight distributes remaining row space. */
public record VexUISize(int width, int height, int weight) {

    /** Validates supported logical dimensions and nonnegative flex weight. */
    public VexUISize {
        if (width < -1 || height < -1 || width > 960 || height > 900 || weight < 0 || weight > 100) {
            throw new IllegalArgumentException("Invalid VexUI dimensions or flex weight");
        }
    }

    /** Uses the content's natural dimensions. */
    public static VexUISize content() {
        return new VexUISize(-1, -1, 0);
    }

    /** Fills a share of the remaining horizontal space. */
    public static VexUISize flexible() {
        return new VexUISize(-1, -1, 1);
    }

    /** Uses exact logical pixel dimensions. */
    public static VexUISize fixed(final int width, final int height) {
        return new VexUISize(width, height, 0);
    }
}
