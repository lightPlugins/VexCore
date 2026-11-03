package dev.vexsoft.core.paper.vexui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Stable, clamped page snapshot; filtering and sorting happen before slicing. */
public record VexUIPagedGrid<T>(List<T> entries, int page, int pages, int total, int pageSize) {

    /** Copies entries and validates a nonempty page range, including for empty results. */
    public VexUIPagedGrid {
        entries = List.copyOf(entries);
        if (pages < 1 || page < 0 || page >= pages || total < entries.size()
            || pageSize < 1 || pageSize > 1000 || entries.size() > pageSize) {
            throw new IllegalArgumentException("Invalid page snapshot");
        }
    }

    /** Creates a snapshot without a known capacity; prefer slice to retain the configured page size. */
    public VexUIPagedGrid(final List<T> entries, final int page, final int pages, final int total) {
        this(entries, page, pages, total, Math.max(1, entries.size()));
    }

    /**
     * Builds equal-height cells and fills unused page slots with invisible, noninteractive spacers.
     * The reserved rows remain stable on partial and empty pages; content must fit the supplied cell height.
     */
    public VexUIGrid grid(
        final int columns,
        final int gap,
        final int cellHeight,
        final Function<? super T, ? extends VexUIElement> renderer
    ) {
        Objects.requireNonNull(renderer, "renderer");
        if (cellHeight < 1) {
            throw new IllegalArgumentException("Paged grid cells need a positive height");
        }
        var cellSize = new VexUISize(-1, cellHeight, 0);
        List<VexUIElement> cells = new ArrayList<>(pageSize);
        for (var entry : entries) {
            cells.add(new VexUIColumn(cellSize, 0, List.of(renderer.apply(entry))));
        }
        while (cells.size() < pageSize) {
            cells.add(new VexUISpacer(cellSize));
        }
        return new VexUIGrid(VexUISize.content(), columns, gap, cells);
    }

    /** Slices a stable ordered result set, preserving a valid page after entries disappear. */
    public static <T> VexUIPagedGrid<T> slice(final List<T> ordered, final int requested, final int pageSize) {
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("Page size must be between 1 and 1000");
        }
        int pages = Math.max(1, (int) ((ordered.size() + (long) pageSize - 1) / pageSize));
        int page = Math.clamp(requested, 0, pages - 1);
        int start = page * pageSize;
        return new VexUIPagedGrid<>(ordered.subList(start, Math.min(start + pageSize, ordered.size())),
            page, pages, ordered.size(), pageSize);
    }
}
