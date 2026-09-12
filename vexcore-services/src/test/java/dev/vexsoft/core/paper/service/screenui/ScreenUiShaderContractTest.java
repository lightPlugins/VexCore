package dev.vexsoft.core.paper.service.screenui;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.paper.screenui.*;
import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

final class ScreenUiShaderContractTest {

    private final Path pack = Path.of(System.getProperty("screenUiPackDirectory"));
    private final V26_2ScreenUiVersionDefinition version = new V26_2ScreenUiVersionDefinition();

    @Test
    void contributedSpritesResolveNamespacedFontsAndRejectInvalidContracts() {
        var sprite = UiTexture.sprite(net.kyori.adventure.key.Key.key("demo:hud/frame"), 180, 56);
        for (int tenth = 5; tenth <= 10; tenth++) {
            for (ScreenAnchor anchor : ScreenAnchor.values()) {
                assertEquals("demo:ui/v26_2/sprite/hud/frame/" + tenth * 10 + "/"
                    + anchor.name().toLowerCase(Locale.ROOT),
                    version.textureFont(sprite, anchor, 8, false, tenth / 10.0).asString());
            }
        }
        assertThrows(IllegalArgumentException.class,
            () -> UiTexture.sprite(net.kyori.adventure.key.Key.key("demo:wide"), 256, 56));
        assertThrows(IllegalArgumentException.class,
            () -> version.textureFont(sprite, ScreenAnchor.TOP_CENTER, 8, true, 0.7));
        assertThrows(IllegalArgumentException.class,
            () -> version.textureFont(sprite, ScreenAnchor.TOP_CENTER, 257, false, 0.7));
        assertNotNull(ScreenUiRenderer.texture(sprite, TextureLayout.builder()
            .anchor(ScreenAnchor.TOP_CENTER).x(-180).y(8).scale(0.7).build(), version));
    }

    @Test
    void transitionPresetsResolveAndDecodeTheirAnchorAfterFloatTransport() throws Exception {
        Properties protocol = new Properties();
        try (var input = Files.newInputStream(pack.resolve("vexcore-screen-ui.properties"))) {
            protocol.load(input);
        }
        assertEquals("8", protocol.getProperty("protocol"));
        int stride = Integer.parseInt(protocol.getProperty("stride"));
        int base = Integer.parseInt(protocol.getProperty("base"));
        int count = Integer.parseInt(protocol.getProperty("maxY")) - Integer.parseInt(protocol.getProperty("minY")) + 1;
        for (UiTransition.Kind mode : UiTransition.Kind.values()) {
            for (int duration : new int[]{2, 4, 8, 16}) {
                for (int tenth = 5; tenth <= 10; tenth++) {
                    for (ScreenAnchor anchor : ScreenAnchor.values()) {
                        var transition = new UiTransition(mode, 499, duration, 0);
                        var font = version.transitionTextFont(anchor, 0, tenth / 10.0, transition);
                        String json = Files.readString(pack.resolve("assets/vexcore/font/" + font.value() + ".json"));
                        var match = Pattern.compile("\"ascent\":(-?\\d+)").matcher(json);
                        assertTrue(match.find());
                        int ascent = Integer.parseInt(match.group(1));
                        int kind = 25 + (mode.ordinal() * 4 + transition.durationIndex()) * 6 + tenth - 5;
                        for (int carrier : new int[]{3, 1000}) {
                            for (int corner : new int[]{0, 14}) {
                                float vertex = (float) (8 - ascent) + carrier + corner;
                                int encoded = (int) Math.floor(vertex / stride) - base;
                                assertEquals(kind * 9 + anchor.ordinal(), encoded / count);
                                assertEquals(0, encoded % count + Integer.parseInt(protocol.getProperty("minY")));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void transitionPayloadRetainsClockOffsetAndTintAcrossClockBoundaries() {
        for (long clock : new long[]{0, 499, 500, 23999, 24000, 48001}) {
            for (int offset : new int[]{-32, -1, 0, 31}) {
                var transition = UiTransition.move(clock + 2, 4, offset);
                var layout = TextBlockLayout.builder().anchor(ScreenAnchor.CENTER).transition(transition).build();
                var rendered = ScreenUiRenderer.text(List.of(Component.text("A", TextColor.color(0xFF0000))), layout, version);
                var glyph = rendered.children().stream()
                    .filter(c -> c instanceof net.kyori.adventure.text.TextComponent t && t.content().equals("A"))
                    .findFirst().orElseThrow();
                int payload = Objects.requireNonNull(glyph.color()).value();
                assertEquals(offset, (payload & 63) - 32);
                assertEquals((clock + 2) % 500, (payload >> 6) & 511);
                assertEquals(7 << 6, payload >> 15);
                double age = ((clock % 24000) - ((payload >> 6) & 511) + 500) % 500;
                if (age > 250) {
                    age -= 500;
                }
                assertEquals(-2, age);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> UiTransition.move(0, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> UiTransition.move(0, 2, 32));
        assertThrows(IllegalArgumentException.class,
            () -> TextBlockLayout.builder().transition(UiTransition.fadeOut(0, 4)).build());
    }

    @Test
    void everyDebugAnchorResolvesToAnExistingFontAndDecodesAfterFloatConversion() throws Exception {
        Properties protocol = new Properties();

        try (var input = Files.newInputStream(pack.resolve("vexcore-screen-ui.properties"))) {
            protocol.load(input);
        }

        int stride = Integer.parseInt(protocol.getProperty("stride"));
        int base = Integer.parseInt(protocol.getProperty("base"));
        int count = Integer.parseInt(protocol.getProperty("maxY")) - Integer.parseInt(protocol.getProperty("minY")) + 1;

        for (ScreenAnchor anchor : ScreenAnchor.values()) {
            int row = anchor.ordinal() / 3;
            int origin = row == 0 ? 8 : row == 1 ? -6 : -20;
            var layout = TextBlockLayout.builder()
                .anchor(anchor)
                .y(row == 0 ? 8 : row == 1 ? 0 : -8)
                .verticalAlignment(VerticalAlignment.values()[row])
                .build();

            assertEquals(origin, ScreenUiRenderer.layout(List.of(Component.text("A")), layout).getFirst().y());
            assertNotNull(ScreenUiRenderer.text(List.of(Component.text("A")), layout, version));

            var font = version.textFont(anchor, origin);
            String json = Files.readString(pack.resolve("assets/vexcore/font/" + font.value() + ".json"));
            var match = Pattern.compile("\"ascent\":(-?\\d+)").matcher(json);

            assertTrue(match.find());

            int ascent = Integer.parseInt(match.group(1));

            for (int carrierY : new int[]{3, 22, 98, 402, 1000}) {
                for (int cornerY : new int[]{0, 14}) {
                    // Vertex positions cross an int -> float boundary in Minecraft; check the actual pack encoding.
                    float vertexY = (float) (8 - ascent) + carrierY + cornerY;
                    int encoded = (int) Math.floor(vertexY / stride) - base;

                    assertEquals(anchor.ordinal(), encoded / count);
                    assertEquals(0, encoded % count + Integer.parseInt(protocol.getProperty("minY")));
                }
            }

            int panelY = row == 0 ? 8 : row == 1 ? -29 : -66;

            assertTrue(Files.exists(pack.resolve(
                "assets/vexcore/font/" + version.textureFont(UiTexture.PANEL, anchor, panelY).value() + ".json")));
        }

        try (var files = Files.walk(pack.resolve("assets/vexcore/font/ui/v26_2"))) {
            assertEquals(927, files.filter(Files::isRegularFile).count(), "Scale and transition presets stay bounded");
        }
    }

    @Test
    void markedAtlasPreservesInkAndMetricsAndContainsBothSignaturePixels() throws Exception {
        var legacy = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/text.png").toFile());
        var marked = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/anchored_text.png").toFile());

        for (int point = 33; point <= 255; point++) {
            if (point > 126 && point < 161) {
                continue;
            }

            int col = (point - 32) % 32;
            int row = (point - 32) / 32;

            assertEquals(ScreenUiFont.advance(point) > 1 ? 0x01565831 : 0, marked.getRGB(col * 16, row * 14));
            assertEquals(ScreenUiFont.advance(point) > 1 ? 0x01434f52 : 0, marked.getRGB(col * 16, row * 14 + 1));

            int ink = 0;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 14; y++) {
                    int pixel = marked.getRGB(col * 16 + x, row * 14 + y);

                    if ((pixel >>> 24) != 0) {
                        ink = Math.max(ink, x + 1);
                    }

                    if (y >= 2) {
                        assertEquals(legacy.getRGB(col * 16 + x, row * 12 + y - 2), pixel);
                    }
                }
            }

            assertEquals(ScreenUiFont.advance(point), ink + 1, "advance for " + point);
        }
    }

    @Test
    void panelUsesCenteredAlphaCanvasAndMatchingScaledAdvance() throws Exception {
        var source = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/panel.png").toFile());
        var anchored = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/anchored_panel.png").toFile());

        assertEquals(256, source.getWidth());
        assertEquals(256, source.getHeight());
        assertEquals(256, anchored.getWidth());
        assertEquals(256, anchored.getHeight());
        assertEquals(0x01565831, anchored.getRGB(0, 0));
        assertEquals(0x01434f52, anchored.getRGB(0, 1));

        int rightmost = -1;

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int pixel = source.getRGB(x, y);

                if ((pixel >>> 24) != 0) {
                    assertTrue(
                        x >= 58 && x < 198 && y >= 104 && y < 152,
                        "Panel must stay centered in its alpha canvas"
                    );

                    rightmost = Math.max(rightmost, x);
                }

                if (x != 0 || y > 1) {
                    assertEquals(pixel, anchored.getRGB(x, y));
                }
            }
        }

        assertEquals((rightmost + 1) * 2 + 1, UiTexture.PANEL.glyphAdvance());
        assertEquals(0, UiTexture.PANEL.glyphOffsetX() + 58 * 2);
        assertEquals(280, 140 * 2);
        assertEquals(96, 48 * 2);

        String json = Files.readString(pack.resolve(
            "assets/vexcore/font/" + version.textureFont(UiTexture.PANEL, ScreenAnchor.BOTTOM_CENTER, -143).value()
                + ".json"));

        assertTrue(json.contains("\"height\":512"));
    }

    @Test
    void unsupportedAssetsAndDecorationsFailBeforePublishing() {
        assertThrows(IllegalArgumentException.class, () -> version.textFont(ScreenAnchor.TOP_LEFT, 257));

        var layout = TextBlockLayout.builder().anchor(ScreenAnchor.TOP_LEFT).y(8).build();

        for (var decoration : List.of(TextDecoration.ITALIC, TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH)) {
            assertThrows(
                IllegalArgumentException.class,
                () -> ScreenUiRenderer.text(List.of(Component.text("A").decorate(decoration)), layout, version)
            );
        }

        assertNotNull(ScreenUiRenderer.text(
            List.of(Component.text("A").decorate(TextDecoration.BOLD)),
            layout,
            version
        ));
    }

    @Test
    void allCardModesHavePackFontsAndNexoSizedTextures() throws Exception {
        for (int height : new int[]{20, 48, 64, 80, 96, 128}) {
            UiTexture card = UiTexture.card(height);

            for (ScreenAnchor anchor : ScreenAnchor.values()) {
                for (boolean compact : new boolean[]{false, true}) {
                    var font = version.textureFont(card, anchor, -143, false, compact);
                    String json = Files.readString(pack.resolve("assets/vexcore/font/" + font.value() + ".json"));
                    var file = Pattern.compile("\"file\":\"vexcore:([^\"]+)\"").matcher(json);

                    assertTrue(file.find());

                    var png = ImageIO.read(pack.resolve("assets/vexcore/textures/" + file.group(1)).toFile());

                    assertEquals(256, png.getWidth());
                    assertEquals(256, png.getHeight());
                    assertEquals(0x01565831, png.getRGB(0, 0));
                    assertEquals(0x01434f52, png.getRGB(0, 1));
                    assertTrue(Files.exists(pack.resolve(
                        "assets/vexcore/font/" + version.textFont(anchor, 0, true, compact).value() + ".json")));

                    if (height == 20) {
                        assertTrue(Files.exists(pack.resolve(
                            "assets/vexcore/font/" + version.textureFont(card, anchor, 0, true, compact).value()
                                + ".json")));
                    }
                }
            }
        }

        UiTexture wrongMetrics = new UiTexture(UiTexture.card(20).fontPrefix(), 0xE100, 200, 20);

        assertThrows(
            IllegalArgumentException.class,
            () -> version.textureFont(wrongMetrics, ScreenAnchor.TOP_RIGHT, 0, false, true)
        );
    }

    @Test
    void scalePresetsResolveForAllGlyphKindsAndSurviveVertexFloatConversion() throws Exception {
        for (int tenth = 5; tenth <= 10; tenth++) {
            double scale = tenth / 10.0;

            for (ScreenAnchor anchor : ScreenAnchor.values()) {
                for (boolean animated : new boolean[]{false, true}) {
                    for (boolean card : new boolean[]{false, true}) {
                        var font = card ? version.textureFont(UiTexture.card(20), anchor, -143, animated, scale)
                            : version.textFont(anchor, -143, animated, scale);
                        String json = Files.readString(pack.resolve("assets/vexcore/font/" + font.value() + ".json"));
                        var match = Pattern.compile("\"ascent\":(-?\\d+)").matcher(json);

                        assertTrue(match.find());

                        int ascent = Integer.parseInt(match.group(1));
                        int expectedKind = tenth == 10 ? (animated ? (card ? 4 : 3) : (card ? 2 : 0))
                            : tenth == 5 ? (animated ? (card ? 8 : 7) : (card ? 6 : 5))
                                : 9 + (tenth - 6) * 4 + (animated ? 2 : 0) + (card ? 1 : 0);

                        for (int carrier : new int[]{3, 98, 1000}) {
                            for (int corner : new int[]{0, card ? 256 : 14}) {
                                float vertexY = (float) (8 - ascent) + carrier + corner;
                                int group = ((int) Math.floor(vertexY / 4096.0) - 1024) / 513;

                                assertEquals(expectedKind, group / 9);
                                assertEquals(anchor.ordinal(), group % 9);
                            }
                        }
                    }
                }
            }
        }

        for (double invalid : new double[]{-1, 0, 0.4, 0.75, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> UiScale.validate(invalid));
        }
    }
}


