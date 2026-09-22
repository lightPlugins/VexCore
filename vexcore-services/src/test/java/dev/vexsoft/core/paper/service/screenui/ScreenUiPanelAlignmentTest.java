package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.configuration.MapConfigurationSection;
import dev.vexsoft.core.paper.screenui.HorizontalAlignment;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.UiNode;
import dev.vexsoft.core.paper.screenui.UiPanelLayout;
import dev.vexsoft.core.paper.screenui.UiPanelStyle;
import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

final class ScreenUiPanelAlignmentTest {

    @Test
    void alignmentDefaultsToLeftAndAcceptsCaseInsensitiveConfiguration() {
        assertEquals(HorizontalAlignment.LEFT, UiPanelLayout.builder().build().textAlign());
        for (HorizontalAlignment alignment : HorizontalAlignment.values()) {
            var config = new MapConfigurationSection(Map.of("text-align", alignment.name().toLowerCase(Locale.ROOT)));
            assertEquals(alignment, UiPanelLayout.parse(config, UiPanelStyle.DEFAULT).textAlign());
        }
        assertThrows(
            IllegalArgumentException.class, () -> UiPanelLayout.parse(
                new MapConfigurationSection(Map.of("text-align", "justified")), UiPanelStyle.DEFAULT)
        );
    }

    @Test
    void everyWrappedLineUsesThePanelContentAreaAtEitherScreenEdge() {
        var content = new UiNode.Group(
            "root", false, List.of(
            new UiNode.Space("track", 120, 16),
            new UiNode.Group(
                "nested", false, List.of(new UiNode.Text(
                "message", List.of(
                Component.text("Alpha beta gamma delta epsilon zeta eta theta"), Component.text("A"))
            ))
            )
        )
        );
        for (ScreenAnchor anchor : List.of(ScreenAnchor.TOP_LEFT, ScreenAnchor.TOP_RIGHT)) {
            var base = UiPanelLayout.builder().anchor(anchor).x(anchor == ScreenAnchor.TOP_LEFT ? 8 : -8)
                .maxWidth(128).style(new UiPanelStyle(false, 0.15, 3, 4, 3)).build();
            var original = ScreenUiPanelRenderer.measure(content, base);
            for (HorizontalAlignment alignment : HorizontalAlignment.values()) {
                var layout = base.toBuilder().textAlign(alignment).build();
                var rendered = ScreenUiPanelRenderer.prepare(content, layout, new V26_2ScreenUiVersionDefinition());
                assertEquals(original, rendered.bounds(), "Alignment must preserve panel size and track placement");
                var lines = new ArrayList<RenderedLine>();
                int[] advance = {0};
                collect(rendered.component(), advance, lines);
                assertEquals(0, advance[0], "The carrier must retain zero total advance");
                assertTrue(lines.size() > 2, "The long paragraph must wrap before the final short line");
                int left = original.x() + layout.style().padding();
                for (RenderedLine line : lines) {
                    int expected = left + switch (alignment) {
                        case LEFT -> 0;
                        case CENTER -> 60 - line.width() / 2;
                        case RIGHT -> 120 - line.width();
                    };
                    assertEquals(expected, line.x(), line.text());
                    assertTrue(line.x() >= left && line.x() + line.width() <= left + 120);
                }
            }
        }
    }

    @Test
    void rowTextStaysWithinItsCellBesideAnAvatarReservation() {
        var content = new UiNode.Group(
            "root", false, List.of(
            new UiNode.Space("track", 120, 1),
            new UiNode.Group(
                "identity", true, List.of(
                new UiNode.Space("avatar", 24, 24),
                new UiNode.Text("details", List.of(Component.text("Player name"), Component.text("1")))
            )
            )
        )
        );
        var layout = UiPanelLayout.builder().maxWidth(128).textAlign(HorizontalAlignment.RIGHT)
            .style(new UiPanelStyle(false, 0.15, 3, 4, 3)).build();
        var rendered = ScreenUiPanelRenderer.prepare(content, layout, new V26_2ScreenUiVersionDefinition());
        var details = rendered.bounds().slots().get("details");
        var avatar = rendered.bounds().slots().get("avatar");
        var lines = new ArrayList<RenderedLine>();
        collect(rendered.component(), new int[]{0}, lines);
        assertEquals(2, lines.size());
        for (RenderedLine line : lines) {
            assertEquals(details.x() + details.width(), line.x() + line.width());
            assertTrue(line.x() >= avatar.x() + avatar.width() + layout.style().gap());
        }
    }

    private static void collect(Component component, int[] advance, List<RenderedLine> lines) {
        if (component instanceof TextComponent text && !text.content().isEmpty() && component.font() != null) {
            if (component.font().asString().equals("vexcore:ui/space")) {
                for (int point : text.content().codePoints().toArray()) {
                    advance[0] += point >= 0xE020 ? -(1 << (point - 0xE020)) : 1 << (point - 0xE000);
                }
            } else {
                int width = text.content().codePoints().map(ScreenUiFont::advance).sum();
                int x = advance[0] - Math.floorDiv(advance[0] + 2048, 4096) * 4096;
                lines.add(new RenderedLine(text.content(), x, width));
                advance[0] += width;
            }
        }
        component.children().forEach(child -> collect(child, advance, lines));
    }

    private record RenderedLine(String text, int x, int width) {

    }
}
