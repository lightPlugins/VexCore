package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.configuration.MapConfigurationSection;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.UiNode;
import dev.vexsoft.core.paper.screenui.UiPanelBounds;
import dev.vexsoft.core.paper.screenui.UiPanelDefinition;
import dev.vexsoft.core.paper.screenui.UiPanelLayout;
import dev.vexsoft.core.paper.screenui.UiPanelSet;
import dev.vexsoft.core.paper.screenui.UiPanelStyle;
import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

public final class ScreenUiPanelTest {

    private final V26_2ScreenUiVersionDefinition version = new V26_2ScreenUiVersionDefinition();

    @Test
    void longColoredQuestWrapsWithoutEllipsisAndMovesFollowingObjectives() {
        UiNode content = new UiNode.Group(
            "quest", false, List.of(
            new UiNode.Group(
                "first", true, List.of(
                new UiNode.Text(
                    "label", Component.text(
                    "Sammle besondere Kristalle in der verlassenen Mine",
                    TextColor.color(0x55AAFF)
                )
                ), new UiNode.Text("count", Component.text("12/50"))
            )
            ),
            new UiNode.Text("second", Component.text("Kehre zum Forscher zurück"))
        )
        );
        var layout = UiPanelLayout.builder().anchor(ScreenAnchor.TOP_RIGHT).x(-8).y(8).maxWidth(150).build();
        var prepared = ScreenUiPanelRenderer.prepare(content, layout, version);
        var bounds = prepared.bounds();
        var label = bounds.slots().get("label");
        var counter = bounds.slots().get("count");
        var next = bounds.slots().get("second");
        assertTrue(label.height() > 12);
        assertEquals(label.y(), counter.y());
        assertTrue(counter.x() >= label.x() + label.width() + layout.style().gap());
        assertTrue(next.y() >= label.y() + label.height() + layout.style().gap());
        assertEquals(-8, bounds.x() + bounds.width());
        assertTrue(bounds.width() <= 150);
        String plain = PlainTextComponentSerializer.plainText()
            .serialize(prepared.component());
        assertFalse(plain.contains("..."));
        assertTrue(plain.contains("Kristalle"));
        assertTrue(plain.contains("verlassenen"));
    }

    @Test
    void invisibleChildrenConsumeNoSpaceAndShortTextShrinksItsBackground() {
        var layout = UiPanelLayout.builder().maxWidth(224).build();
        var shortPanel = ScreenUiPanelRenderer.measure(new UiNode.Text("text", Component.text("Hi")), layout);
        assertTrue(shortPanel.width() < 40);
        var group = new UiNode.Group(
            "root", false, List.of(
            new UiNode.Text("empty", Component.empty()),
            new UiNode.Text("text", Component.text("Hi"))
        )
        );
        assertEquals(shortPanel.height(), ScreenUiPanelRenderer.measure(group, layout).height());
        var hidden = ScreenUiPanelRenderer.measure(new UiNode.Group("empty", false, List.of()), layout);
        assertEquals(0, hidden.height());
    }

    @Test
    void configurationResolvesAddedLocalizedFieldsAndConditions() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("name", Map.of("type", "text"));
        fields.put("extra", Map.of("type", "text", "show-if", "enabled"));
        var definition = UiPanelDefinition.parse(
            new MapConfigurationSection(Map.of(
                "content",
                Map.of("type", "column", "children", fields)
            )), UiPanelStyle.DEFAULT, "profile"
        );
        var visible = definition.resolve(key -> List.of(Component.text(key)), Map.of(), Map.of("enabled", true));
        var hidden = definition.resolve(key -> List.of(Component.text(key)), Map.of(), Map.of("enabled", false));
        assertTrue(ScreenUiPanelRenderer.measure(visible, definition.layout()).height()
            > ScreenUiPanelRenderer.measure(hidden, definition.layout()).height());
        assertThrows(IllegalArgumentException.class, () -> new UiPanelStyle(true, Double.NaN, 3, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> new UiPanelStyle(true, 1.01, 3, 4, 3));
    }

    @Test
    void automaticFieldKeysSurviveGroupingAndKeepEveryLocalizedLine() {
        var text = Map.of("type", "text");
        var nested = Map.of("type", "column", "children", Map.of("dust", text));
        var flat = UiPanelDefinition.parse(
            new MapConfigurationSection(Map.of("content", nested)),
            UiPanelStyle.DEFAULT, "screen-ui.profile"
        );
        var grouped = UiPanelDefinition.parse(
            new MapConfigurationSection(Map.of(
                "content",
                Map.of("type", "column", "children", Map.of("details", nested))
            )),
            UiPanelStyle.DEFAULT, "screen-ui.profile"
        );
        List<Component> localized = List.of(Component.text("Dust: 42"), Component.text("Income: 7/s"));
        Function<String, List<Component>> resolver = key -> {
            assertEquals("screen-ui.profile.dust", key);
            return localized;
        };
        var flatNode = (UiNode.Group) flat.resolve(resolver, Map.of(), Map.of());
        var groupedNode = (UiNode.Group) grouped.resolve(resolver, Map.of(), Map.of());
        var inner = (UiNode.Group) groupedNode.children().getFirst();
        assertEquals(localized, ((UiNode.Text) flatNode.children().getFirst()).lines());
        assertEquals(localized, ((UiNode.Text) inner.children().getFirst()).lines());
        assertEquals(
            ScreenUiPanelRenderer.measure(flatNode, flat.layout()).height(),
            ScreenUiPanelRenderer.measure(groupedNode, grouped.layout()).height()
        );
        var duplicated = new MapConfigurationSection(Map.of(
            "content", Map.of(
                "type", "column",
                "children", Map.of("left", nested, "right", nested)
            )
        ));
        assertThrows(
            IllegalArgumentException.class,
            () -> UiPanelDefinition.parse(duplicated, UiPanelStyle.DEFAULT, "screen-ui.profile")
        );
    }

    @Test
    void panelPayloadPreservesExactDimensionsOpacityAndRadius() {
        var layout = UiPanelLayout.builder().anchor(ScreenAnchor.CENTER).build();
        Component background = ScreenUiPanelRenderer.background(224, 197, layout, version);
        var glyph = background.children().stream().filter(value -> value instanceof TextComponent text
            && text.content().equals(Character.toString(0xE103))).findFirst().orElseThrow();
        int payload = glyph.color().value();
        assertEquals(224, (payload >> 15) + 1);
        assertEquals(197, ((payload >> 7) & 255) + 1);
        assertEquals(15, payload & 127);
    }

    @Test
    void everyPanelAndAvatarFontSurvivesFloatTransportAndHasMarkedPixels() throws Exception {
        Path pack = Path.of(System.getProperty("screenUiPackDirectory"));
        for (int tenth = 5; tenth <= 10; tenth++) {
            for (ScreenAnchor anchor : ScreenAnchor.values()) {
                for (boolean avatar : List.of(false, true)) {
                    var font = avatar ? version.pixelFont(anchor, 8, tenth / 10.0)
                        : version.panelFont(anchor, 8, tenth / 10.0, 3);
                    String json = Files.readString(pack.resolve("assets/vexcore/font/" + font.value() + ".json"));
                    var match = Pattern.compile("\"ascent\":(-?\\d+)").matcher(json);
                    assertTrue(match.find());
                    int ascent = Integer.parseInt(match.group(1));
                    for (int carrier : new int[]{3, 1000}) {
                        float vertex = (float) (8 - ascent) + carrier + 14;
                        int encoded = (int) Math.floor(vertex / 4096) - 1024;
                        assertEquals(((avatar ? 85 : 79) + tenth - 5) * 9 + anchor.ordinal(), encoded / 513);
                        assertEquals(256, encoded % 513);
                    }
                }
            }
        }
        var atlas = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/rounded.png").toFile());
        for (int radius = 0; radius <= 8; radius++) {
            assertEquals(0x01565831, atlas.getRGB(radius * 16, 0));
            assertEquals(0x01434F52, atlas.getRGB(radius * 16, 1));
            assertEquals(radius, (atlas.getRGB(radius * 16 + 1, 0) >> 16) & 255);
        }
    }

    @Test
    void sharedAnchorPanelsReflowAfterTextGrowthAndSkipIdleUpdates() {
        var definitions = new LinkedHashMap<String, UiPanelDefinition>();
        for (String id : List.of("first", "second")) {
            definitions.put(
                id, UiPanelDefinition.parse(
                    new MapConfigurationSection(Map.of(
                        "max-width", 150, "content", Map.of("type", "text"))), UiPanelStyle.DEFAULT, id
                )
            );
        }
        var positions = new HashMap<String, UiPanelBounds>();
        int[] writes = {0};
        var screen = (ScreenUi) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{ScreenUi.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("panel")) {
                    throw new AssertionError(method.getName());
                }
                var bounds = ScreenUiPanelRenderer.measure((UiNode) args[1], (UiPanelLayout) args[2]);
                positions.put((String) args[0], bounds);
                writes[0]++;
                return bounds;
            }
        );
        var panels = new UiPanelSet(screen, definitions);
        assertTrue(panels.bounds("first").isEmpty());
        var content = new HashMap<String, Component>();
        content.put("first", Component.text("Short"));
        content.put("second", Component.text("Next"));
        panels.render(key -> List.of(content.get(key.substring(0, key.indexOf('.')))), Map.of(), Map.of());
        int originalY = positions.get("panel.second").y();
        panels.render(key -> List.of(content.get(key.substring(0, key.indexOf('.')))), Map.of(), Map.of());
        assertEquals(2, writes[0]);
        content.put("first", Component.text("Several words that now wrap onto more than one line"));
        panels.render(key -> List.of(content.get(key.substring(0, key.indexOf('.')))), Map.of(), Map.of());
        var first = positions.get("panel.first");
        var second = positions.get("panel.second");
        assertEquals(first, panels.bounds("first").orElseThrow());
        assertEquals(second, panels.bounds("second").orElseThrow());
        assertTrue(panels.bounds("missing").isEmpty());
        assertTrue(second.y() > originalY);
        assertTrue(second.y() >= first.y() + first.height() + 3);
    }

    @Test
    void avatarComposesTheHatLayerAndRejectsUnexpectedImageDimensions() throws Exception {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(8, 8, 0xFF0000FF);
        skin.setRGB(40, 8, 0x80FF0000);
        var output = new ByteArrayOutputStream();
        ImageIO.write(skin, "PNG", output);
        assertEquals(0x80007F, ScreenUiAvatarLoader.decode(output.toByteArray()).pixels().getFirst());
        output.reset();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "PNG", output);
        assertEquals(64, ScreenUiAvatarLoader.decode(output.toByteArray()).pixels().size());
    }
}

