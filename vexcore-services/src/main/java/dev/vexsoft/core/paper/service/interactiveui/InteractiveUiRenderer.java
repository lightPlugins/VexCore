package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Compiles independent scene and cursor carriers for the interactive UI shader protocol. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractiveUiRenderer {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;
    public static final int MAX_ELEMENTS = 128;
    static final int CHARACTER_ADVANCE = 7;
    private static final int STATIONARY_CURSOR_PAYLOAD = 63 << 7 | 63;
    private static final Key TEXT_FONT = Key.key("vexcore:interactive_ui/text");
    private static final Key RECTANGLE_FONT = Key.key("vexcore:interactive_ui/rectangle");
    private static final Key CURSOR_FONT = Key.key("vexcore:interactive_ui/cursor");
    private static final Key SPACE_FONT = Key.key("vexcore:interactive_ui/space");

    /** Checks the complete scene budget without allocating rendered glyph components during element updates. */
    public static void validateScene(List<InteractiveUiElement> elements) {
        if (elements.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("An interactive scene supports at most " + MAX_ELEMENTS + " elements");
        }
        int remaining = 4096;

        for (InteractiveUiElement element : elements) {
            remaining = validateLabel(element.label(), remaining, 0);
        }
    }

    /** Renders scene geometry only; callers retain this component until the scene or hover changes. */
    public static Component renderScene(List<InteractiveUiElement> elements, String hoveredId) {
        Objects.requireNonNull(elements, "elements");
        if (elements.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("An interactive scene supports at most " + MAX_ELEMENTS + " elements");
        }
        TextComponent.Builder output = Component.text();
        int[] remainingCharacters = {4096};
        for (InteractiveUiElement element : elements) {
            var bounds = element.bounds();
            int color = element.backgroundColor();
            if (element.interactive() && element.id().equals(hoveredId)) {
                color = brighten(color);
            }
            appendRectangle(output, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
            int columns = Math.max(0, ((int) bounds.width() - 8) / CHARACTER_ADVANCE);
            int rows = Math.max(0, ((int) bounds.height() - 4) / 9);
            if (columns > 0 && rows > 0) {
                LabelWriter writer = new LabelWriter(output, bounds.x() + 4, bounds.y() + 3, columns, rows);
                appendLabel(element.label(), Style.empty(), writer, remainingCharacters, 0);
            }
        }
        return finish(output);
    }

    /** Renders the latest cursor position without adding client-side interpolation delay. */
    public static Component renderCursor(double cursorX, double cursorY) {
        requirePosition(cursorX, cursorY);
        // Zero deltas also make older versions of the resource pack display the target immediately.
        Component glyph = Component.text("\uE000").font(CURSOR_FONT).color(TextColor.color(STATIONARY_CURSOR_PAYLOAD));
        return finish(Component.text().append(position(glyph, cursorX, cursorY, 5)));
    }

    /** Compatibility overload; previous positions and clock values no longer delay cursor placement. */
    public static Component renderCursor(
        double cursorX,
        double cursorY,
        double previousCursorX,
        double previousCursorY,
        long gameTick
    ) {
        return renderCursor(cursorX, cursorY);
    }

    static int rectanglePayload(double width, double height, int color) {
        int alpha = color >>> 24;
        if (alpha == 0) {
            alpha = 255;
        }
        return Math.clamp((int) Math.round(width), 1, WIDTH)
            | Math.clamp((int) Math.round(height), 1, HEIGHT) << 9 | (alpha * 127 / 255) << 17;
    }

    static int paletteIndex(int color) {
        return ((color >>> 20) & 15) << 8 | ((color >>> 12) & 15) << 4 | ((color >>> 4) & 15);
    }

    private static void appendRectangle(
        TextComponent.Builder output,
        double x,
        double y,
        double width,
        double height,
        int color
    ) {
        Component glyph = Component.text(Character.toString(0xE000 + paletteIndex(color)))
            .font(RECTANGLE_FONT).color(TextColor.color(rectanglePayload(width, height, color)));
        output.append(position(glyph, x, y, 5));
    }

    private static Component position(Component glyph, double x, double y, int advance) {
        // The horizontal carrier transports Y in quarter-pixel units while every operation has zero net advance.
        int transport = (int) Math.round((x - WIDTH / 2.0) * 4) + (int) Math.round(y * 4) * 4096;
        return Component.empty().append(space(transport)).append(glyph).append(space(-transport - advance * 4));
    }

    static Component space(int quarterPixels) {
        if (Math.abs((long) quarterPixels) > 4194303) {
            throw new IllegalArgumentException("Interactive UI carrier exceeds its coordinate budget");
        }
        StringBuilder encoded = new StringBuilder();
        int amount = Math.abs(quarterPixels);
        for (int bit = 0; amount != 0; bit++, amount >>>= 1) {
            if ((amount & 1) != 0) {
                encoded.appendCodePoint((quarterPixels < 0 ? 0xE020 : 0xE000) + bit);
            }
        }
        return Component.text(encoded.toString()).font(SPACE_FONT);
    }

    private static void appendLabel(
        Component component,
        Style inherited,
        LabelWriter writer,
        int[] remaining,
        int depth
    ) {
        if (depth > 32 || --remaining[0] < 0) {
            throw new IllegalArgumentException("Interactive UI labels exceed their component budget");
        }
        if (!(component instanceof TextComponent text)) {
            throw new IllegalArgumentException("Resolve translations before rendering interactive UI labels");
        }
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        TextColor color = style.color() == null ? NamedTextColor.WHITE : style.color();
        for (int point : text.content().codePoints().toArray()) {
            if (--remaining[0] < 0) {
                throw new IllegalArgumentException("Interactive UI labels exceed 4096 characters");
            }
            writer.append(point, color);
        }
        for (Component child : component.children()) {
            appendLabel(child, style, writer, remaining, depth + 1);
        }
    }

    private static int validateLabel(Component component, int remaining, int depth) {
        if (!(component instanceof TextComponent text)) {
            throw new IllegalArgumentException("Resolve translations before rendering interactive UI labels");
        }
        remaining -= 1 + text.content().codePointCount(0, text.content().length());
        if (depth > 32 || remaining < 0) {
            throw new IllegalArgumentException("Interactive UI labels exceed their component budget");
        }
        for (Component child : component.children()) {
            remaining = validateLabel(child, remaining, depth + 1);
        }
        return remaining;
    }

    private static Component finish(TextComponent.Builder output) {
        return output.build().color(NamedTextColor.WHITE).shadowColor(ShadowColor.none())
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.UNDERLINED, false).decoration(TextDecoration.STRIKETHROUGH, false)
            .decoration(TextDecoration.OBFUSCATED, false);
    }

    private static int brighten(int color) {
        return color & 0xFF000000 | Math.min(255, ((color >>> 16) & 255) + 24) << 16
            | Math.min(255, ((color >>> 8) & 255) + 24) << 8 | Math.min(255, (color & 255) + 24);
    }

    private static void requirePosition(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > WIDTH || y < 0 || y > HEIGHT) {
            throw new IllegalArgumentException("Cursor coordinates must stay inside the 320 by 180 canvas");
        }
    }

    @RequiredArgsConstructor
    private static final class LabelWriter {

        private final TextComponent.Builder output;
        private final double x;
        private final double y;
        private final int columns;
        private final int rows;
        private int column;
        private int row;

        private void append(int point, TextColor color) {
            if (point == '\n') {
                this.column = 0;
                this.row++;
                return;
            }
            if (this.column == this.columns) {
                this.column = 0;
                this.row++;
            }
            if (this.row >= this.rows) {
                return;
            }
            int displayed = point >= 32 && point <= 126 || point >= 160 && point <= 255 ? point : '?';
            Component glyph = Component.text(Character.toString(displayed)).font(TEXT_FONT).color(color);
            this.output.append(position(glyph, this.x + this.column * CHARACTER_ADVANCE, this.y + this.row * 9, 7));
            this.column++;
        }
    }
}
