package dev.vexsoft.core.paper.service.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

/** Checks the actual generated pack rather than assuming a texture filename implies a valid font contract. */
final class UpgradeAnimationPackTest {

    @Test
    void streamedBackgroundMusicMatchesTheSuppliedVorbisFile() throws Exception {
        var pack = Path.of(System.getProperty("screenUiPackDirectory"));
        var source = Path.of("..", "resource-pack-tools", "sounds", "upgrader_background.ogg");
        var shipped = pack.resolve("assets/vexcore/sounds/upgrade/upgrader_background.ogg");
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(shipped));
        var definition = new ObjectMapper()
            .readTree(Files.readString(pack.resolve("assets/vexcore/sounds.json")));
        var sound = definition.path("upgrade.background").path("sounds").get(0);
        assertEquals("vexcore:upgrade/upgrader_background", sound.path("name").asText());
        assertTrue(sound.path("stream").asBoolean(), "Long stereo music must use streaming playback");
    }

    @Test
    void everyRitualSpriteFits256PixelsAndHasItsOwnedFontAndMetadata() throws Exception {
        var pack = Path.of(System.getProperty("screenUiPackDirectory"));
        var version = new V26_2ScreenUiVersionDefinition();
        var names = new ArrayList<String>();
        names.add("frame");
        for (int frame = 0; frame < 16; frame++) {
            names.add("core_" + frame);
            names.add("burst_" + frame);
            names.add("success_" + frame);
        }
        for (int rune = 1; rune <= 3; rune++) {
            names.add("rune_" + rune);
            names.add("rune_" + rune + "_inactive");
        }
        for (int fill = 0; fill <= 180; fill++) {
            names.add("bar_" + fill);
        }
        assertEquals(236, names.size());
        for (var name : names) {
            var image = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/upgrade/" + name + ".png").toFile());
            assertNotNull(image);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertEquals(0x01565831, image.getRGB(0, 0));
            assertEquals(0x01434F52, image.getRGB(0, 1));
            var sprite = UiTexture.sprite(Key.key("vexcore:upgrade/" + name), 180, 12);
            var font = version.textureFont(sprite, ScreenAnchor.CENTER, 58, false, 1);
            var definition = pack.resolve("assets/vexcore/font/" + font.value() + ".json");
            assertTrue(Files.isRegularFile(definition));
            assertTrue(Files.readString(definition).contains("vexcore:ui/upgrade/" + name + ".png"));
        }
        var empty = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/upgrade/bar_0.png").toFile());
        var full = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/upgrade/bar_180.png").toFile());
        assertEquals(0, empty.getRGB(90, 5));
        assertTrue((full.getRGB(92, 5) >>> 24) > 100);
    }

    @Test
    void everyForgeAndBurstSilhouetteRetainsLeftRightSymmetryAndTheApprovedArtwork() throws Exception {
        for (int frame = 0; frame < 16; frame++) {
            for (String name : new String[]{"core_" + frame, "burst_" + frame}) {
                var image = content(name, 72, 72);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(image.getRGB(x, y) >>> 24, image.getRGB(71 - x, y) >>> 24,
                            name + " horizontal silhouette at " + x + "," + y);
                    }
                }
                assertTrue(visiblePixels(image) > 0, name + " must contain visible pixels");
            }
        }
        var approved = ImageIO.read(Path.of("..", "resource-pack-tools", "artwork", "upgrade-forge.png").toFile());
        assertArrayEquals(approved.getRGB(0, 0, 72, 72, null, 0, 72),
            content("core_0", 72, 72).getRGB(0, 0, 72, 72, null, 0, 72),
            "The uncharged forge must preserve the approved pixel artwork");
    }

    @Test
    void successShowersExpandBeyondTheForgeRemainMirroredAndFadeBeforeTheFinalHold() throws Exception {
        int peak = 0;
        var distinct = new HashSet<String>();
        boolean reachesBothSides = false;
        for (int phase = 0; phase < 16; phase++) {
            var effect = content("success_" + phase, 184, 104);
            assertAlphaMirrorsHorizontally(effect, "success_" + phase);
            peak = Math.max(peak, visiblePixels(effect));
            distinct.add(Arrays.toString(effect.getRGB(0, 0, 184, 104, null, 0, 184)));
            for (int y = 0; y < 104; y++) {
                for (int x = 0; x < 184; x++) {
                    if ((effect.getRGB(x, y) >>> 24) != 0) {
                        reachesBothSides |= x < 30 || x >= 154;
                    }
                }
            }
        }
        assertTrue(peak > 400, "Success needs a broad shower rather than the former two small stars");
        assertTrue(reachesBothSides, "The celebration extends beyond the compact central forge");
        assertTrue(distinct.size() >= 14, "The showers and shockwaves must visibly progress");
        assertEquals(0, visiblePixels(content("success_15", 184, 104)), "The finale settles before closing");
        var source = content("core_0", 72, 72);
        for (int phase = 0; phase < 16; phase++) {
            var burst = content("burst_" + phase, 72, 72);
            for (int y = 35; y < 72; y++) {
                for (int x = 0; x < 72; x++) {
                    assertEquals(source.getRGB(x, y), burst.getRGB(x, y),
                        "Celebration must preserve the anvil and its readable percentage face");
                }
            }
        }
    }

    @Test
    void largerFreestandingGlyphsAndSquarePanelKeepMirroredGeometry() throws Exception {
        var glyphs = new HashSet<String>();
        for (int rune = 1; rune <= 3; rune++) {
            var image = content("rune_" + rune, 22, 26);
            var inactive = content("rune_" + rune + "_inactive", 22, 26);
            int visible = visiblePixels(image);
            assertTrue(visible > 220 && visible < 22 * 26 * 0.9,
                "Thick letter strokes must retain negative spaces rather than becoming a solid slot");
            var colors = new HashSet<Integer>();
            for (int y = 0; y < 26; y++) {
                for (int x = 0; x < 22; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    assertTrue(alpha == 0 || alpha == 255, "Glyph pixels must not be antialiased");
                    assertEquals(alpha, inactive.getRGB(x, y) >>> 24, "Activation must not change the letterform");
                    if (alpha != 0) {
                        colors.add(image.getRGB(x, y));
                    }
                }
            }
            assertEquals(3, colors.size(), "Every glyph needs a body, light edge, and shadow edge");
            glyphs.add(Arrays.toString(image.getRGB(0, 0, 22, 26, null, 0, 22)));
        }
        assertEquals(3, glyphs.size(), "Each glyph must have its own pixel design");
        var bar = content("bar_180", 180, 12);
        assertAlphaMirrors(bar, "full progress bar");
        assertEquals(0xFFC9A8DD, bar.getRGB(0, 0), "The fill has a soft top highlight");
        assertEquals(0xFF70517F, bar.getRGB(179, 11), "The fill has a subtle bottom shadow");
        var empty = ImageIO.read(texture("bar_0").toFile());
        for (int y = 2; y < empty.getHeight(); y++) {
            for (int x = 0; x < empty.getWidth(); x++) {
                assertEquals(0, empty.getRGB(x, y), "An empty bar must contain no visible or hidden RGB pixels");
            }
        }
        var frame = content("frame", 224, 240);
        assertAlphaMirrors(frame, "panel silhouette");
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int edgeX = Math.min(x, 223 - x);
                int edgeY = Math.min(y, 239 - y);
                assertEquals(edgeX == 0 && edgeY == 0 ? 0 : 255, frame.getRGB(x, y) >>> 24,
                    "Only one pixel is cut from each square panel corner");
                if (edgeX >= 3 && edgeY >= 3 && y != 61 && y != 62 && (y < 177 || y > 190)
                    && (y < 166 || y > 168)) {
                    assertEquals(0xFFC6C6C6, frame.getRGB(x, y), "Panel interior must be flat Minecraft gray");
                }
            }
        }
        assertEquals(0xFFFFFFFF, frame.getRGB(1, 100), "Left bevel faces the light");
        assertEquals(0xFF555555, frame.getRGB(222, 100), "Right bevel faces the shadow");
        assertEquals(0xFFBDBDBD, frame.getRGB(22, 178), "Empty track stays close to the gray background");
        assertEquals(0xFFBDBDBD, frame.getRGB(201, 189), "Track fits all 180 columns and twelve rows");
        var forge = content("core_0", 72, 72);
        assertEquals(255, forge.getRGB(36, 14) >>> 24, "The forge has a levitating central amethyst");
        assertEquals(0, forge.getRGB(36, 29) >>> 24, "A clear gap separates the crystal from the anvil");
        assertEquals(255, forge.getRGB(36, 64) >>> 24, "The anvil retains its solid foot");
    }

    @Test
    void everyFillStateAddsExactlyOneContinuousPixelColumnWithoutSegmentGaps() throws Exception {
        for (int fill = 0; fill <= 180; fill++) {
            var image = content("bar_" + fill, 180, 12);
            for (int x = 0; x < 180; x++) {
                for (int y = 0; y < 12; y++) {
                    assertEquals(x < fill ? 255 : 0, image.getRGB(x, y) >>> 24,
                        "Continuous square bar differs at fill " + fill + ", column " + x + ", row " + y);
                }
            }
        }
    }

    @Test
    void theSixteenFrameCoreLoopHasNoClosingJumpOrDuplicateOnlyAnimation() throws Exception {
        var frames = new ArrayList<BufferedImage>();
        var distinct = new HashSet<String>();
        for (int index = 0; index < 16; index++) {
            var image = content("core_" + index, 72, 72);
            frames.add(image);
            distinct.add(Arrays.toString(image.getRGB(0, 0, 72, 72, null, 0, 72)));
        }
        assertTrue(distinct.size() >= 8, "Sixteen pulse frames must contain at least eight distinct visual states");
        long largestTransition = 0;
        for (int index = 1; index < frames.size(); index++) {
            largestTransition = Math.max(largestTransition, rgbaDifference(frames.get(index - 1), frames.get(index)));
        }
        assertTrue(largestTransition > 0, "The pulse must actually animate");
        assertTrue(largestTransition <= 72L * 72 * 4 * 255 / 10,
            "An adjacent pulse frame must change at most ten percent of the full RGBA range");
        long closingTransition = rgbaDifference(frames.getLast(), frames.getFirst());
        assertTrue(closingTransition <= largestTransition,
            "core_15 -> core_0 must not jump farther than any normal adjacent transition");
    }

    private static BufferedImage content(final String name, final int width, final int height) throws Exception {
        var image = ImageIO.read(texture(name).toFile());
        assertNotNull(image, name);
        return image.getSubimage(0, 2, width, height);
    }

    private static Path texture(final String name) {
        return Path.of(System.getProperty("screenUiPackDirectory"))
            .resolve("assets/vexcore/textures/ui/upgrade/" + name + ".png");
    }

    private static void assertAlphaMirrors(final BufferedImage image, final String name) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                assertEquals(alpha, image.getRGB(image.getWidth() - 1 - x, y) >>> 24,
                    name + " horizontal silhouette at " + x + "," + y);
                assertEquals(alpha, image.getRGB(x, image.getHeight() - 1 - y) >>> 24,
                    name + " vertical silhouette at " + x + "," + y);
            }
        }
    }

    private static void assertAlphaMirrorsHorizontally(final BufferedImage image, final String name) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(image.getRGB(x, y), image.getRGB(image.getWidth() - 1 - x, y),
                    name + " mirrored pixels at " + x + "," + y);
            }
        }
    }

    private static int visiblePixels(final BufferedImage image) {
        int visible = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0) {
                    visible++;
                }
            }
        }
        return visible;
    }

    private static long rgbaDifference(final BufferedImage first, final BufferedImage second) {
        long difference = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                int before = first.getRGB(x, y);
                int after = second.getRGB(x, y);
                for (int channel = 0; channel < 4; channel++) {
                    difference += Math.abs((before >>> (channel * 8) & 255) - (after >>> (channel * 8) & 255));
                }
            }
        }
        return difference;
    }
}
