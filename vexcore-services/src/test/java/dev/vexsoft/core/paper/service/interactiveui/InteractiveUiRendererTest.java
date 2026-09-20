package dev.vexsoft.core.paper.service.interactiveui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.UiBounds;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

final class InteractiveUiRendererTest {

    private final Path pack = Path.of(System.getProperty("interactiveUiPackDirectory"));

    @Test
    void generatedAtlasKeepsTheExactAdvanceUsedByTheRenderer() throws Exception {
        var atlas = ImageIO.read(this.pack.resolve("assets/vexcore/textures/interactive_ui/text.png").toFile());
        for (int point = 32; point <= 255; point++) {
            int x = (point - 32) % 16 * 6;
            int y = (point - 32) / 16 * 10;
            assertEquals(0x01564955, atlas.getRGB(x, y));
            assertEquals(0x01434F52, atlas.getRGB(x, y + 1));
            int inkWidth = 0;
            for (int column = 0; column < 6; column++) {
                for (int row = 0; row < 10; row++) {
                    if ((atlas.getRGB(x + column, y + row) >>> 24) != 0) {
                        inkWidth = Math.max(inkWidth, column + 1);
                    }
                }
            }
            assertEquals(InteractiveUiRenderer.CHARACTER_ADVANCE, inkWidth + 1);
        }
    }

    @Test
    void generatedFontKindsSurviveFloatConversionAndDifferentBossbarOffsets() throws Exception {
        String[] fonts = {"text", "rectangle", "cursor"};
        for (int kind = 0; kind < fonts.length; kind++) {
            String json = Files.readString(this.pack.resolve("assets/vexcore/font/interactive_ui/" + fonts[kind] + ".json"));
            var match = Pattern.compile("\"ascent\":(-?\\d+)").matcher(json);
            assertTrue(match.find());
            int ascent = Integer.parseInt(match.group(1));
            for (int carrierY : new int[]{3, 22, 79, 400, 1000}) {
                for (int corner : new int[]{0, kind == 0 ? 10 : 4}) {
                    float transported = (float) (8 - ascent) + carrierY + corner;
                    assertEquals(kind, (int) Math.floor(transported / 4096.0) - 512);
                }
            }
        }
    }

    @Test
    void quarterPixelCoordinatesSurviveTheClientFloatCarrierAtAllCanvasEdges() {
        for (int guiWidth : new int[]{320, 427, 640, 1920}) {
            for (double x : new double[]{0, 0.25, 160, 319.75, 320}) {
                for (double y : new double[]{0, 0.25, 90, 179.75, 180}) {
                    float carrier = (float) (Math.floor(guiWidth / 2.0) + x - 160 + Math.round(y * 4) * 1024);
                    double localX = carrier - Math.floor(guiWidth / 2.0);
                    double transportedY = Math.floor((localX + 512) / 1024);
                    assertEquals(x - 160, localX - transportedY * 1024, 0.001);
                    assertEquals(y, transportedY * 0.25, 0.001);
                }
            }
        }
    }

    @Test
    void sceneAndCursorRemainSeparateAndEveryCarrierHasZeroNetAdvance() {
        var element = new InteractiveUiElement("button", new UiBounds(8, 8, 84, 24),
            Component.text("Button", NamedTextColor.YELLOW), 0x234567, true);
        Component normal = InteractiveUiRenderer.renderScene(List.of(element), null);
        Component hovered = InteractiveUiRenderer.renderScene(List.of(element), "button");
        assertNotEquals(normal, hovered);
        assertEquals(0, advance(normal), 0.00001);
        Component cursor = InteractiveUiRenderer.renderCursor(319.75, 179.75);
        assertEquals(0, advance(cursor), 0.00001);
        assertTrue(containsFont(normal, "interactive_ui/rectangle"));
        assertTrue(containsFont(cursor, "interactive_ui/cursor"));
        assertTrue(!containsFont(cursor, "interactive_ui/rectangle"));
    }

    @Test
    void cursorUsesTheLatestTargetRegardlessOfMovementHistoryOrClock() {
        Component cursor = InteractiveUiRenderer.renderCursor(20, 30);
        for (long tick : new long[]{0, 998, 999, 1000, 23999, 24000, 48001, Long.MAX_VALUE}) {
            assertEquals(cursor, InteractiveUiRenderer.renderCursor(20, 30, 10, 35, tick));
            assertEquals(cursor, InteractiveUiRenderer.renderCursor(20, 30, 320, 180, tick));
        }
        assertNotEquals(cursor, InteractiveUiRenderer.renderCursor(21, 30));
        assertNotEquals(cursor, InteractiveUiRenderer.renderCursor(20, 31));
        Component glyph = Objects.requireNonNull(findFont(cursor, "interactive_ui/cursor"));
        int payload = Objects.requireNonNull(glyph.color()).value();
        // Older packs multiply these movement offsets by a clock-based factor; zero keeps them at the target.
        assertEquals(0, (payload & 127) - 63);
        assertEquals(0, ((payload >> 7) & 127) - 63);
    }

    @Test
    void generatedPaletteAndRectangleDimensionsMatchEncodedPayload() throws Exception {
        var atlas = ImageIO.read(this.pack.resolve("assets/vexcore/textures/interactive_ui/rectangle.png").toFile());
        int color = 0xA34FBC;
        int index = InteractiveUiRenderer.paletteIndex(color);
        int pixel = atlas.getRGB(index % 64 * 4 + 1, index / 64 * 4);
        assertEquals(0xFFAA44BB, pixel);
        int payload = InteractiveUiRenderer.rectanglePayload(320, 180, color);
        assertEquals(320, payload & 511);
        assertEquals(180, (payload >> 9) & 255);
        assertEquals(127, payload >> 17);
    }

    @Test
    void generatedCompatibilityShaderRetainsBothDispatchersAndRejectsInvalidCursorData() throws Exception {
        String vertex = Files.readString(this.pack.resolve("assets/minecraft/shaders/core/text.vsh"));
        assertTrue(vertex.contains("if (interactiveKind < 0) position = vex_screen_ui_position"));
        assertTrue(vertex.contains("#moj_import <vexcore:interactive_ui/v26_2.glsl>"));
        assertTrue(Files.exists(this.pack.resolve("assets/vexcore/shaders/include/screen_ui/v26_2.glsl")));
        String interactive = Files.readString(this.pack.resolve("assets/vexcore/shaders/include/interactive_ui/v26_2.glsl"));
        assertFalse(interactive.contains("GameTime"));
        assertFalse(interactive.contains("previousOffset"));
        assertThrows(IllegalArgumentException.class,
            () -> InteractiveUiRenderer.renderCursor(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class,
            () -> InteractiveUiRenderer.renderCursor(321, 0));
    }

    private static Component findFont(Component component, String name) {
        if (component.font() != null && component.font().value().equals(name)) {
            return component;
        }
        return component.children().stream().map(child -> findFont(child, name))
            .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static boolean containsFont(Component component, String name) {
        return findFont(component, name) != null;
    }

    private static double advance(Component component) {
        double result = 0;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            String font = Objects.requireNonNull(component.font()).value();
            for (int point : text.content().codePoints().toArray()) {
                if (font.equals("interactive_ui/space")) {
                    result += point >= 0xE020 ? -(1 << (point - 0xE020)) / 4.0 : (1 << (point - 0xE000)) / 4.0;
                } else {
                    result += font.equals("interactive_ui/text") ? 7 : 5;
                }
            }
        }
        for (Component child : component.children()) {
            result += advance(child);
        }
        return result;
    }
}
