package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIColumn;
import dev.vexsoft.core.paper.vexui.VexUIElement;
import dev.vexsoft.core.paper.vexui.VexUIGrid;
import dev.vexsoft.core.paper.vexui.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.VexUIPanel;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.VexUILayoutException;
import dev.vexsoft.core.paper.vexui.VexUIStyleSheet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Measures nested boxes and allocates flexible row widths without using inventory slots. */
@RequiredArgsConstructor
public final class VexUILayout {

    private final VexUITextEngine text;
    private final VexUIStyleSheet style;

    public VexUILayout(final VexUITextEngine text) {
        this(text, VexUIStyleSheet.defaults());
    }

    public VexUIPlacement layout(final VexUIElement element, final int width) {
        String path = "root/" + element.getClass().getSimpleName().substring(5);
        return place(element, 0, 0, width, height(element, width, path), path);
    }

    private VexUIPlacement place(
        final VexUIElement node,
        final int x,
        final int y,
        final int width,
        final int height,
        final String path
    ) {
        try {
            return placeNode(node, x, y, width, height, path);
        } catch (VexUILayoutException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new VexUILayoutException(path, exception);
        }
    }

    private VexUIPlacement placeNode(
        final VexUIElement node,
        final int x,
        final int y,
        final int width,
        final int height,
        final String path
    ) {
        List<VexUIPlacement> children = new ArrayList<>();
        if (node instanceof VexUIRow row) {
            int[] widths = rowWidths(row, width, path);
            int childX = x;
            for (int i = 0; i < row.children().size(); i++) {
                var child = row.children().get(i);
                String childPath = childPath(path, child, i);
                int childHeight = Math.min(height, height(child, widths[i], childPath));
                children.add(place(child, childX, y + (height - childHeight) / 2, widths[i], childHeight, childPath));
                childX += widths[i] + row.gap();
            }
        } else if (node instanceof VexUIColumn column) {
            int childY = y;
            for (int i = 0; i < column.children().size(); i++) {
                var child = column.children().get(i);
                String childPath = childPath(path, child, i);
                int childWidth = child.size().width() < 0 ? width : Math.min(width, child.size().width());
                int childHeight = height(child, childWidth, childPath);
                if (childY + childHeight > y + height) {
                    throw new IllegalArgumentException("Column content exceeds its fixed height");
                }
                children.add(place(child, x, childY, childWidth, childHeight, childPath));
                childY += childHeight + column.gap();
            }
        } else if (node instanceof VexUIGrid grid) {
            if (grid.children().stream().anyMatch(VexUIGridCell.class::isInstance)) {
                var plan = spans(grid, width, path);
                for (var cell : plan.cells()) {
                    children.add(place(cell.node(), x + cell.column() * (plan.width() + grid.gap()),
                        y + cell.row() * (plan.height() + grid.gap()),
                        plan.width() * cell.columns() + grid.gap() * (cell.columns() - 1),
                        plan.height() * cell.rows() + grid.gap() * (cell.rows() - 1), cell.path()));
                }
                return new VexUIPlacement(node, new VexUIBounds(x, y, width, height), children);
            }
            int cellWidth = gridWidth(grid, width);
            int childY = y;
            for (int start = 0; start < grid.children().size(); start += grid.columns()) {
                int end = Math.min(start + grid.columns(), grid.children().size());
                int rowHeight = 0;
                for (int i = start; i < end; i++) {
                    var child = grid.children().get(i);
                    rowHeight = Math.max(rowHeight, height(child, cellWidth, childPath(path, child, i)));
                }
                for (int i = start; i < end; i++) {
                    children.add(place(grid.children().get(i), x + (i - start) * (cellWidth + grid.gap()),
                        childY, cellWidth, rowHeight, childPath(path, grid.children().get(i), i)));
                }
                childY += rowHeight + grid.gap();
            }
        } else if (node instanceof VexUIPanel panel) {
            children.add(inset(panel.content(), x, y, width, height, panel.padding(), path));
        } else if (node instanceof VexUIButton button) {
            children.add(inset(button.content(), x, y, width, height, button.padding(), path));
        } else if (node instanceof VexUIGridCell cell) {
            children.add(place(cell.content(), x, y, width, height, childPath(path, cell.content(), 0)));
        }
        return new VexUIPlacement(node, new VexUIBounds(x, y, width, height), children);
    }

    private VexUIPlacement inset(
        final VexUIElement child,
        final int x,
        final int y,
        final int width,
        final int height,
        final int padding,
        final String parent
    ) {
        String path = childPath(parent, child, 0);
        int innerWidth = Math.max(0, width - padding * 2);
        int innerHeight = Math.max(0, height - padding * 2);
        int desired = height(child, innerWidth, path);
        if (desired > innerHeight) {
            throw new IllegalArgumentException("Panel or button content exceeds its fixed height");
        }
        return place(child, x + padding, y + padding + (innerHeight - desired) / 2, innerWidth, desired, path);
    }

    private int height(final VexUIElement node, final int width, final String path) {
        try {
            return measureHeight(node, width, path);
        } catch (VexUILayoutException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new VexUILayoutException(path, exception);
        }
    }

    private int measureHeight(final VexUIElement node, final int width, final String path) {
        if (node.size().height() >= 0) {
            return node.size().height();
        }
        if (node instanceof VexUIText) {
            return 12;
        }
        if (node instanceof VexUIParagraph paragraph) {
            return text.wrap(paragraph.text(), width, paragraph.maximumLines()).size() * 12;
        }
        if (node instanceof VexUIGridCell cell) {
            return height(cell.content(), width, childPath(path, cell.content(), 0));
        }
        if (node instanceof VexUIItemIcon icon) {
            return icon.pixels();
        }
        if (node instanceof VexUIPanel panel) {
            return height(panel.content(), Math.max(0, width - 2 * panel.padding()), childPath(path, panel.content(), 0))
                + 2 * panel.padding();
        }
        if (node instanceof VexUIButton button) {
            return Math.max(style.buttonHeight(),
                height(button.content(), Math.max(0, width - 2 * button.padding()),
                    childPath(path, button.content(), 0)) + 2 * button.padding());
        }
        if (node instanceof VexUIColumn column) {
            int result = gaps(column.children(), column.gap());
            for (int i = 0; i < column.children().size(); i++) {
                var child = column.children().get(i);
                result += height(child, child.size().width() < 0 ? width : Math.min(width, child.size().width()),
                    childPath(path, child, i));
            }
            return result;
        }
        if (node instanceof VexUIRow row) {
            int[] widths = rowWidths(row, width, path);
            int result = 0;
            for (int i = 0; i < widths.length; i++) {
                var child = row.children().get(i);
                result = Math.max(result, height(child, widths[i], childPath(path, child, i)));
            }
            return result;
        }
        if (node instanceof VexUIGrid grid) {
            if (grid.children().stream().anyMatch(VexUIGridCell.class::isInstance)) {
                var plan = spans(grid, width, path);
                return plan.rows() * plan.height() + Math.max(0, plan.rows() - 1) * grid.gap();
            }
            int result = 0;
            for (int start = 0; start < grid.children().size(); start += grid.columns()) {
                int rowHeight = 0;
                for (int i = start; i < Math.min(start + grid.columns(), grid.children().size()); i++) {
                    var child = grid.children().get(i);
                    rowHeight = Math.max(rowHeight, height(child, gridWidth(grid, width), childPath(path, child, i)));
                }
                result += rowHeight + (start == 0 ? 0 : grid.gap());
            }
            return result;
        }
        return 0;
    }

    private int naturalWidth(final VexUIElement node, final String path) {
        if (node.size().width() >= 0) {
            return node.size().width();
        }
        if (node instanceof VexUIGridCell cell) {
            return naturalWidth(cell.content(), childPath(path, cell.content(), 0));
        }
        if (node instanceof VexUIText label) {
            return text.width(label.text());
        }
        if (node instanceof VexUIParagraph paragraph) {
            return text.width(paragraph.text());
        }
        if (node instanceof VexUIPanel panel) {
            return naturalWidth(panel.content(), childPath(path, panel.content(), 0)) + panel.padding() * 2;
        }
        if (node instanceof VexUIButton button) {
            return naturalWidth(button.content(), childPath(path, button.content(), 0)) + 2 * button.padding();
        }
        if (node instanceof VexUIRow row) {
            int result = gaps(row.children(), row.gap());
            for (int i = 0; i < row.children().size(); i++) {
                result += naturalWidth(row.children().get(i), childPath(path, row.children().get(i), i));
            }
            return result;
        }
        if (node instanceof VexUIColumn column) {
            int result = 0;
            for (int i = 0; i < column.children().size(); i++) {
                result = Math.max(result,
                    naturalWidth(column.children().get(i), childPath(path, column.children().get(i), i)));
            }
            return result;
        }
        if (node instanceof VexUIGrid grid) {
            int result = 0;
            for (int i = 0; i < grid.children().size(); i++) {
                result = Math.max(result,
                    naturalWidth(grid.children().get(i), childPath(path, grid.children().get(i), i)));
            }
            return result * grid.columns() + (grid.columns() - 1) * grid.gap();
        }
        return 0;
    }

    private int[] rowWidths(final VexUIRow row, final int width, final String path) {
        int[] result = new int[row.children().size()];
        int used = gaps(row.children(), row.gap());
        int weight = 0;
        for (int i = 0; i < result.length; i++) {
            var child = row.children().get(i);
            if (child.size().weight() > 0) {
                weight += child.size().weight();
            } else {
                result[i] = naturalWidth(child, childPath(path, child, i));
                used += result[i];
            }
        }
        if (used > width) {
            throw new IllegalArgumentException("Row's fixed content exceeds its available width");
        }
        int remaining = width - used;
        for (int i = 0; i < result.length; i++) {
            int childWeight = row.children().get(i).size().weight();
            if (childWeight > 0) {
                result[i] = remaining * childWeight / weight;
                remaining -= result[i];
                weight -= childWeight;
            }
        }
        return result;
    }

    private int gridWidth(final VexUIGrid grid, final int width) {
        int result = (width - (grid.columns() - 1) * grid.gap()) / grid.columns();
        if (result < 1) {
            throw new IllegalArgumentException("Grid has no room for its columns");
        }
        return result;
    }

    private int gaps(final List<?> children, final int gap) {
        return Math.max(0, children.size() - 1) * gap;
    }

    private GridPlan spans(final VexUIGrid grid, final int width, final String path) {
        List<SpannedCell> cells = new ArrayList<>();
        List<boolean[]> occupied = new ArrayList<>();
        int rowHeight = 1;
        int cellWidth = gridWidth(grid, width);
        for (int childIndex = 0; childIndex < grid.children().size(); childIndex++) {
            var child = grid.children().get(childIndex);
            String childPath = childPath(path, child, childIndex);
            int columns = child instanceof VexUIGridCell cell ? cell.columns() : 1;
            int rows = child instanceof VexUIGridCell cell ? cell.rows() : 1;
            if (columns > grid.columns()) {
                throw new IllegalArgumentException("Cell span exceeds the grid's column count");
            }
            int index = 0;
            while (!available(occupied, index / grid.columns(), index % grid.columns(), rows, columns, grid.columns())) {
                index++;
            }
            int row = index / grid.columns();
            int column = index % grid.columns();
            for (int y = row; y < row + rows; y++) {
                for (int x = column; x < column + columns; x++) {
                    occupied.get(y)[x] = true;
                }
            }
            int contentHeight = height(child, cellWidth * columns + grid.gap() * (columns - 1), childPath);
            rowHeight = Math.max(rowHeight, Math.ceilDiv(Math.max(1, contentHeight - grid.gap() * (rows - 1)), rows));
            cells.add(new SpannedCell(child, row, column, rows, columns, childPath));
        }
        return new GridPlan(cells, cellWidth, rowHeight, occupied.size());
    }

    private boolean available(final List<boolean[]> occupied, final int row, final int column,
                              final int rows, final int columns, final int maximumColumns) {
        if (column + columns > maximumColumns) {
            return false;
        }
        while (occupied.size() < row + rows) {
            occupied.add(new boolean[maximumColumns]);
        }
        for (int y = row; y < row + rows; y++) {
            for (int x = column; x < column + columns; x++) {
                if (occupied.get(y)[x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private String childPath(final String parent, final VexUIElement child, final int index) {
        return parent + "/" + child.getClass().getSimpleName().substring(5) + "[" + index + "]";
    }

    private record SpannedCell(VexUIElement node, int row, int column, int rows, int columns, String path) {
    }

    private record GridPlan(List<SpannedCell> cells, int width, int height, int rows) {
    }
}
