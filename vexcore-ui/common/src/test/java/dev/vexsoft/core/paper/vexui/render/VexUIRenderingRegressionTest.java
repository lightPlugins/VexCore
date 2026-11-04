package dev.vexsoft.core.paper.vexui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.session.VexUIPackPreview;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

/** Guards asset lookup compatibility, styled text runs and exact pixels after render batching. */
public final class VexUIRenderingRegressionTest {

    @Test
    void flatStatBackgroundsKeepTheirFullTextHeightWithoutWeakeningFramedInsets() {
        var flat = new VexUITheme(0x38303F, 0x38303F, 0x38303F, 0x38303F);
        var row = new VexUIPanel(VexUISize.fixed(190, 14), 0, flat,
            new VexUIText(new VexUISize(-1, 12, 0), Component.text("Fortune"), false));
        var frame = new VexUIFrame(Component.empty(), Component.empty(), 190, row, Map.of());
        assertEquals(14, new VexUILayout(new VexUITextEngine(new VexUIMetrics()))
            .layout(frame.content(), frame.width()).bounds().height());
        assertThrows(IllegalArgumentException.class, () -> new VexUIPanel(
            VexUISize.fixed(190, 14), 0, VexUITheme.PANEL, new VexUIText(Component.text("Fortune"))));
    }

    @Test
    void underlineFollowsShiftedTextBelowTheGlyphsAtEveryVerticalOffset() throws Exception {
        var metrics = new VexUIMetrics();
        int color = 0xBEA3D1;
        var label = Component.text("Rezept", TextColor.color(color)).decorate(TextDecoration.UNDERLINED);
        int width = new VexUITextEngine(metrics).width(label);
        for (int offset = 0; offset < 9; offset++) {
            var content = new VexUIColumn(0, new VexUISpacer(VexUISize.fixed(200, offset)),
                new VexUIText(new VexUISize(200, 12, 0), label, false));
            var frame = new VexUIFrame(Component.empty(), Component.empty(), 200, content, Map.of());
            var rendered = new VexUIRenderer(metrics).render(frame, action -> null);
            var image = new VexUIPackPreview().render(rendered);
            for (int x = 0; x < width; x++) {
                assertEquals(0xFF000000 | color, image.getRGB(x, offset + 10), "Underline at " + offset + ":" + x);
            }
            assertEquals(0, image.getRGB(width, offset + 10));
        }
        var decorated = new VexUITextEngine(metrics).decorations(label);
        assertEquals(1, decorated.spans().size(), "One colored run should paint one line, not one tile per letter");
        assertEquals(width, decorated.spans().getFirst().width());
    }

    @Test
    public void parsedMeasurementsRetainAllGeneratedGlyphWidthsAndMissingResourceChecks() throws Exception {
        var properties = new Properties();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
            "/dev/vexsoft/core/paper/vexui/metrics.properties"))) {
            properties.load(input);
        }
        var metrics = new VexUIMetrics();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("text.") && Character.isDigit(key.charAt(5))) {
                int point = Integer.parseInt(key.substring(5));
                int advance = Integer.parseInt(properties.getProperty(key));
                assertEquals(point, metrics.character(point));
                assertEquals(advance, metrics.advance(point, false));
                assertEquals(advance + 1, metrics.advance(point, true));
            } else if (key.startsWith("icon.") && !key.endsWith(".advance")) {
                int separator = key.lastIndexOf('.');
                Key sprite = Key.key(key.substring(5, separator));
                int size = Integer.parseInt(key.substring(separator + 1));
                assertEquals(Integer.parseInt(properties.getProperty(key)), metrics.icon(sprite, size));
                assertEquals(Integer.parseInt(properties.getProperty(key + ".advance")), metrics.iconAdvance(sprite, size));
            }
        }
        assertEquals('?', metrics.character(-1));
        assertThrows(IllegalArgumentException.class, () -> metrics.tile(0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> metrics.icon(Key.key("test:missing"), 16));
    }

    @Test
    public void groupedPanelStripsPaintEveryOriginalBorderAndBackgroundPixel() throws Exception {
        var theme = VexUITheme.PANEL;
        var size = VexUISize.fixed(337, 49);
        var frame = new VexUIFrame(Component.text("Panel"), Component.text("Close"), 337,
            new VexUIPanel(size, 3, theme, new VexUISpacer(VexUISize.fixed(331, 43))), Map.of());
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            action -> ClickEvent.custom(Key.key("test", action), null));
        var image = new VexUIPackPreview().render(rendered);
        for (int y = 0; y < 49; y++) {
            for (int x = 0; x < 337; x++) {
                int color = x == 0 || y == 0 || x == 336 || y == 48 ? theme.outerBorder()
                    : x == 335 || y == 47 ? theme.darkBorder()
                    : x == 1 || y == 1 ? theme.lightBorder() : theme.background();
                assertEquals(0xFF000000 | color, image.getRGB(x, y), "Panel pixel " + x + "," + y);
            }
        }
    }

    @Test
    public void groupedTextRetainsNestedColorBoldAndHoverEvents() {
        var color = TextColor.color(0xBEA3D1);
        var tooltip = Component.text("Tooltip");
        var input = Component.text("Normal ").append(Component.text("Wichtig").color(color)
            .decorate(TextDecoration.BOLD).hoverEvent(tooltip));
        var output = new VexUITextEngine(new VexUIMetrics()).fit(input, 300, 0);
        var styled = new StringBuilder();
        ComponentFlattener.basic().flatten(output, new FlattenerListener() {
            private int depth;

            @Override
            public void pushStyle(final Style style) {
                if (color.equals(style.color())) {
                    assertEquals(TextDecoration.State.TRUE, style.decoration(TextDecoration.BOLD));
                    assertEquals(tooltip.asHoverEvent(), style.hoverEvent());
                    depth++;
                }
            }

            @Override
            public void component(final String text) {
                if (depth > 0) {
                    styled.append(text);
                }
            }

            @Override
            public void popStyle(final Style style) {
                if (color.equals(style.color())) {
                    depth--;
                }
            }
        });
        assertEquals("Wichtig", styled.toString());
    }
}
