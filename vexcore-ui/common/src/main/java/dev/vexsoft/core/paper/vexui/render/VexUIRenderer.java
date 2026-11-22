package dev.vexsoft.core.paper.vexui.render;

import static dev.vexsoft.core.paper.vexui.element.VexUISpaces.shift;

import dev.vexsoft.core.paper.vexui.VexUIDebugOptions;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import dev.vexsoft.core.paper.vexui.element.VexUIHotspot;
import dev.vexsoft.core.paper.vexui.element.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIParagraph;
import dev.vexsoft.core.paper.vexui.element.VexUIProgressBar;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.layout.VexUIBounds;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.layout.VexUIPlacement;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/** Composes themed rectangles, localized text and hit geometry into one padded dialog body. */
@RequiredArgsConstructor
public final class VexUIRenderer {

    private static final Key[] TILE_FONTS = IntStream.range(0, 900)
        .mapToObj(offset -> Key.key("vexcore:vexui/tiles/y_" + offset)).toArray(Key[]::new);
    private static final Key[] ICON_FONTS = IntStream.range(0, 9)
        .mapToObj(offset -> Key.key("vexcore:vexui/icons/y_" + offset)).toArray(Key[]::new);
    private static final Component HOTSPOT_EDGE = Component.text("\uE040")
        .font(Key.key("vexcore:vexui/space")).shadowColor(ShadowColor.none());

    private final VexUIMetrics metrics;

    public List<Component> wrap(final Component text, final int width) {
        return new VexUITextEngine(metrics).wrap(text, width, 0);
    }

    public int fittedTextWidth(final Component text, final int maximumWidth) {
        return new VexUITextEngine(metrics).fitMeasured(text, maximumWidth, 0).width();
    }

    public VexUIRenderedFrame render(final VexUIFrame frame, final Function<String, ClickEvent> actions) {
        return render(frame, (action, sound) -> actions.apply(action));
    }

    public VexUIRenderedFrame render(
        final VexUIFrame frame,
        final BiFunction<String, VexUIClickSound, ClickEvent> actions
    ) {
        return render(frame, actions, VexUIStyleSheet.defaults(), VexUIDebugOptions.disabled());
    }

    public VexUIRenderedFrame render(
        final VexUIFrame frame,
        final BiFunction<String, VexUIClickSound, ClickEvent> actions,
        final VexUIStyleSheet style,
        final VexUIDebugOptions debug
    ) {
        var text = new VexUITextEngine(metrics);
        var layout = new VexUILayout(text, style).layout(frame.content(), frame.width());
        var canvas = new Canvas(frame, layout.bounds().height() + 12, text, actions, style, debug);
        canvas.paint(layout, null, Component.empty(), false, "root");
        if (debug.bounds()) {
            canvas.outlines(layout, 0);
        }
        return new VexUIRenderedFrame(canvas.body(), frame.width(), canvas.height, canvas.regions);
    }

    private final class Canvas {
        private final VexUIFrame frame;
        private final int height;
        private final VexUITextEngine text;
        private final BiFunction<String, VexUIClickSound, ClickEvent> actions;
        private final VexUIStyleSheet style;
        private final VexUIDebugOptions debug;
        private final List<List<Part>> lines;
        private final List<Part> backgrounds = new ArrayList<>();
        private final List<VexUIRenderedFrame.HitRegion> regions = new ArrayList<>();

        private Canvas(
            final VexUIFrame frame,
            final int height,
            final VexUITextEngine text,
            final BiFunction<String, VexUIClickSound, ClickEvent> actions,
            final VexUIStyleSheet style,
            final VexUIDebugOptions debug
        ) {
            this.frame = frame;
            this.height = height;
            this.text = text;
            this.actions = actions;
            this.style = style;
            this.debug = debug;
            if (height > 900) {
                throw new IllegalArgumentException("VexUI canvas is taller than 900 pixels");
            }
            lines = new ArrayList<>();
            for (int i = 0; i < (height + 8) / 9; i++) {
                lines.add(new ArrayList<>());
            }
        }

        private void paint(
            final VexUIPlacement placement,
            final ClickEvent inherited,
            final Component inheritedTooltip,
            final boolean disabled,
            final String path
        ) {
            var bounds = placement.bounds();
            var node = placement.element();
            ClickEvent click = inherited;
            Component tooltip = inheritedTooltip;
            boolean muted = disabled;
            if (node instanceof VexUIHotspot hotspot) {
                if (bounds.y() % 9 != 0 || bounds.height() % 9 != 0) {
                    throw new IllegalArgumentException("Native space hit targets must align with text rows");
                }
                click = null;
                tooltip = hotspot.tooltip();
                if (hotspot.action() != null) {
                    click = actions.apply(hotspot.action(), null);
                    regions.add(new VexUIRenderedFrame.HitRegion(bounds, hotspot.action()));
                    tooltip = actionTooltip(tooltip, path, hotspot.action(), bounds);
                }
                nativeRows(bounds, click, tooltip);
            } else if (node instanceof VexUIButton button) {
                muted = disabled || !button.enabled();
                click = muted ? null : actions.apply(button.action(), button.clickSound());
                tooltip = actionTooltip(button.tooltip(), path, button.action(), bounds);
                // A nested disabled button must not leave an enabled ancestor clickable underneath it.
                if (muted && inherited != null) {
                    throw new IllegalArgumentException("Disabled buttons cannot be nested in clickable buttons");
                }
                if (bounds.width() < 2 || bounds.height() < 9) {
                    throw new IllegalArgumentException("Buttons need a hit area of at least 2 x 9 pixels");
                }
                var skin = muted ? style.disabled() : button.role() == null ? button.theme() : style.theme(button.role());
                if (button.attention().highlighted()) {
                    skin = new VexUITheme(skin.background(), skin.outerBorder(), 0xC5A164, 0x82683F);
                }
                if (click != null || !tooltip.equals(Component.empty())) {
                    hit(bounds, click, tooltip, skin.background());
                }
                if (click != null) {
                    regions.add(new VexUIRenderedFrame.HitRegion(bounds, button.action()));
                }
                panel(bounds, skin);
            } else if (node instanceof VexUIPanel panel) {
                if (!panel.tooltip().equals(Component.empty())) {
                    tooltip = panel.tooltip();
                    if (click == null && bounds.y() % 9 == 0 && bounds.height() % 9 == 0
                        && (panel.content() instanceof VexUIItemIcon || panel.content() instanceof VexUITexture)) {
                        nativeRows(bounds, click, tooltip);
                    } else {
                        hit(bounds, click, tooltip, panel.theme().background());
                    }
                }
                panel(bounds, panel.theme());
            } else if (node instanceof VexUIProgressBar bar) {
                rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bar.track());
                int filled = (int) Math.round(bounds.width() * bar.progress());
                if (bar.segments() == 0) {
                    rect(bounds.x(), bounds.y(), filled, bounds.height(), bar.fill());
                } else {
                    for (int i = 0; i < bar.segments(); i++) {
                        int start = i * bounds.width() / bar.segments();
                        int end = (i + 1) * bounds.width() / bar.segments() - 1;
                        rect(bounds.x() + start, bounds.y(), Math.min(end, filled) - start, bounds.height(), bar.fill());
                    }
                }
            } else if (node instanceof VexUIParagraph paragraph) {
                var wrapped = text.wrap(paragraph.text(), bounds.width(), paragraph.maximumLines());
                for (int i = 0; i < wrapped.size(); i++) {
                    int y = bounds.y() + i * 12;
                    var line = text.fitMeasured(wrapped.get(i), bounds.width(), y % 9);
                    label(bounds.x(), y, line.component(), line.width(), muted);
                }
            } else if (node instanceof VexUIText label) {
                var labelText = text.fitMeasured(label.text(), bounds.width(), bounds.y() % 9);
                Component fitted = labelText.component();
                int advance = labelText.width();
                int x = label.centered() ? bounds.x() + Math.max(0, (bounds.width() - advance) / 2) : bounds.x();
                // Native empty-space hit areas ignore a font's vertical offset. The background supplies the
                // button's complete, correctly positioned action area instead of adding actions to label spaces.
                label(x, bounds.y(), fitted, advance, muted);
            } else if (node instanceof VexUIItemIcon icon) {
                int point = metrics.icon(icon.sprite(), icon.pixels());
                Component glyph = iconGlyph(point, bounds.y());
                Component effectiveTooltip = icon.tooltip().equals(Component.empty()) ? tooltip : icon.tooltip();
                add(bounds.x(), bounds.y(), events(glyph, click, effectiveTooltip),
                    metrics.iconAdvance(icon.sprite(), icon.pixels()));
            } else if (node instanceof VexUITexture texture) {
                Component glyph = Component.text(Character.toString(texture.codePoint()))
                    .font(Key.key(texture.fontPrefix().namespace(), texture.fontPrefix().value() + "/y_" + (bounds.y() % 9)))
                    .color(texture.tint()).shadowColor(ShadowColor.none());
                if (texture.outgoing()) {
                    glyph = Component.text().append(shift(VexUIEntrance.EXIT_DISPLACEMENT)).append(glyph)
                        .append(shift(-VexUIEntrance.EXIT_DISPLACEMENT)).build();
                }
                Component effective = texture.tooltip().equals(Component.empty()) ? tooltip : texture.tooltip();
                add(bounds.x(), bounds.y(), events(glyph, click, effective), texture.advance());
            }
            for (int i = 0; i < placement.children().size(); i++) {
                var child = placement.children().get(i);
                paint(child, click, tooltip, muted,
                    path + "/" + child.element().getClass().getSimpleName().substring(5) + "[" + i + "]");
            }
        }

        private void outlines(final VexUIPlacement placement, final int depth) {
            var bounds = placement.bounds();
            if (bounds.width() > 0 && bounds.height() > 0) {
                int color = switch (depth % 3) {
                    case 0 -> 0xB69ACB;
                    case 1 -> 0xA8C98D;
                    default -> 0xF2C46C;
                };
                rect(bounds.x(), bounds.y(), bounds.width(), 1, color);
                rect(bounds.x(), bounds.y() + bounds.height() - 1, bounds.width(), 1, color);
                rect(bounds.x(), bounds.y(), 1, bounds.height(), color);
                rect(bounds.x() + bounds.width() - 1, bounds.y(), 1, bounds.height(), color);
            }
            for (var child : placement.children()) {
                outlines(child, depth + 1);
            }
        }

        private void label(final int x, final int y, final Component value, final int advance, final boolean muted) {
            var decorated = text.decorations(value, frame.textShadow());
            var component = decorated.component();
            if (muted) {
                component = component.color(TextColor.color(0x81798D));
            }
            add(x, y, component, advance);
            for (var span : decorated.spans()) {
                int color = muted ? 0x81798D : span.color();
                if (span.underline()) {
                    rect(x + span.x(), y + 10, span.width(), 1, color);
                }
                if (span.strike()) {
                    rect(x + span.x(), y + 6, span.width(), 1, color);
                }
            }
        }

        private void panel(final VexUIBounds b, final VexUITheme theme) {
            rect(b.x(), b.y(), b.width(), b.height(), theme.background());
            int edge = theme.lightBorder();
            rect(b.x(), b.y(), b.width(), 1, theme.outerBorder());
            rect(b.x(), b.y() + b.height() - 1, b.width(), 1, theme.outerBorder());
            rect(b.x(), b.y(), 1, b.height(), theme.outerBorder());
            rect(b.x() + b.width() - 1, b.y(), 1, b.height(), theme.outerBorder());
            if (b.width() > 4 && b.height() > 4) {
                rect(b.x() + 1, b.y() + 1, b.width() - 2, 1, edge);
                rect(b.x() + 1, b.y() + 1, 1, b.height() - 2, edge);
                rect(b.x() + 1, b.y() + b.height() - 2, b.width() - 2, 1, theme.darkBorder());
                rect(b.x() + b.width() - 2, b.y() + 1, 1, b.height() - 2, theme.darkBorder());
            }
        }

        private void rect(final int x, final int y, final int w, final int h, final int color) {
            if (w <= 0 || h <= 0) {
                return;
            }
            if (h < 9) {
                for (int row = 0; row < h; row++) {
                    strip(x, y + row, w, color, 1, null, Component.empty());
                }
            } else {
                for (int row = 0; row < h; row += 9) {
                    strip(x, y + Math.min(row, h - 9), w, color, 0, null, Component.empty());
                }
            }
        }

        private Component actionTooltip(final Component tooltip, final String path, final String action, final VexUIBounds bounds) {
            if (!debug.actions()) {
                return tooltip;
            }
            return tooltip.append(Component.newline()).append(Component.text(path + " | " + action
                + " | " + bounds.width() + "x" + bounds.height() + " @ " + bounds.x() + "," + bounds.y())
                .color(TextColor.color(0xBEA3D1)));
        }

        private void nativeRows(final VexUIBounds bounds, final ClickEvent click, final Component tooltip) {
            // Minecraft culls rows against their bitmap bounds before it picks styled spaces.
            var strip = Component.text().append(HOTSPOT_EDGE).append(shift(-1))
                .append(events(shift(bounds.width()), click, tooltip))
                .append(shift(-1)).append(HOTSPOT_EDGE).build();
            for (int y = bounds.y(); y < bounds.y() + bounds.height(); y += 9) {
                add(bounds.x(), y, strip, bounds.width());
            }
        }

        private void hit(final VexUIBounds bounds, final ClickEvent click, final Component tooltip, final int color) {
            for (int row = 0; row < bounds.height(); row += 9) {
                strip(bounds.x(), bounds.y() + Math.min(row, bounds.height() - 9), bounds.width(), color, 2, click, tooltip);
            }
        }

        private void strip(final int x, final int y, final int width, final int color, final int kind,
                           final ClickEvent click, final Component tooltip) {
            int remaining = width;
            StringBuilder hitGlyphs = kind == 2 ? new StringBuilder() : null;
            var strip = Component.text().font(TILE_FONTS[y]).color(TextColor.color(color)).shadowColor(ShadowColor.none());
            boolean first = true;
            while (remaining > 0) {
                int tileWidth = Math.min(256, Integer.highestOneBit(remaining));
                if (kind == 2) {
                    if (remaining == 3) {
                        tileWidth = 3;
                    } else if (remaining - tileWidth == 1) {
                        tileWidth = tileWidth == 2 ? 3 : tileWidth / 2;
                    }
                }
                int point = metrics.tile(0, kind, tileWidth);
                if (kind == 2) {
                    hitGlyphs.appendCodePoint(point);
                } else if (first) {
                    strip.content(Character.toString(point));
                } else {
                    // Share strip styling instead of repeating its font/color/shadow for every tile.
                    strip.append(shift(-1)).append(Component.text(Character.toString(point)));
                }
                first = false;
                remaining -= tileWidth;
            }
            if (kind == 2) {
                // Hit glyphs advance by their exact width, so adjacent tiles form one styled/event-bearing run.
                var glyph = Component.text(hitGlyphs.toString()).font(TILE_FONTS[y])
                    .color(TextColor.color(color)).shadowColor(ShadowColor.none());
                backgrounds.add(new Part(x, events(glyph, click, tooltip), width));
            } else {
                backgrounds.add(new Part(x, strip.build(), width + 1));
            }
        }

        private Component body() {
            var result = Component.text();
            for (int row = 0; row < lines.size(); row++) {
                if (row != 0) {
                    result.append(Component.newline());
                }
                int cursor = 0;
                if (row == 0) {
                    for (var part : backgrounds) {
                        result.append(shift(part.x() - cursor)).append(part.value());
                        cursor = part.x() + part.advance();
                    }
                }
                for (var part : lines.get(row)) {
                    result.append(shift(part.x() - cursor)).append(part.value());
                    cursor = part.x() + part.advance();
                }
                // Every line has the same measured width, so the native widget cannot recenter individual rows.
                result.append(shift(frame.width() - cursor));
            }
            return result.build();
        }

        private void add(final int x, final int y, final Component value, final int advance) {
            if (x < 0 || y < 0 || y >= height || x + advance > frame.width() + 1) {
                throw new IllegalArgumentException("Paint operation escaped the VexUI canvas");
            }
            lines.get(y / 9).add(new Part(x, value, advance));
        }

        private Component events(final Component component, final ClickEvent click, final Component tooltip) {
            Component result = component.clickEvent(click);
            if (!tooltip.equals(Component.empty())) {
                result = result.hoverEvent(frame.styleText(tooltip));
            }
            return result;
        }
    }

    private Component iconGlyph(final int point, final int y) {
        return Component.text(Character.toString(point)).font(ICON_FONTS[y % 9])
            .color(TextColor.color(0xFFFFFF)).shadowColor(ShadowColor.shadowColor(0));
    }

    private record Part(int x, Component value, int advance) {
    }
}
