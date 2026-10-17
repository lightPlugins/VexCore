package dev.vexsoft.core.paper.service.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void everyRitualSpriteFits256PixelsAndHasItsOwnedFontAndMetadata() throws Exception {
        var pack = Path.of(System.getProperty("screenUiPackDirectory"));
        var version = new V26_2ScreenUiVersionDefinition();
        var names = new ArrayList<String>();
        names.add("frame");
        for (int frame = 0; frame < 16; frame++) {
            names.add("core_" + frame);
            names.add("burst_" + frame);
        }
        for (int rune = 1; rune <= 3; rune++) {
            names.add("rune_" + rune);
        }
        for (int fill = 0; fill <= 180; fill++) {
            names.add("bar_" + fill);
        }
        assertEquals(217, names.size());
        for (var name : names) {
            var image = ImageIO.read(pack.resolve("assets/vexcore/textures/ui/upgrade/" + name + ".png").toFile());
            assertNotNull(image);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertEquals(0x01565831, image.getRGB(0, 0));
            assertEquals(0x01434F52, image.getRGB(0, 1));
            var sprite = UiTexture.sprite(Key.key("vexcore:upgrade/" + name), 180, 8);
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
    void everyCoreAndBurstFrameHasExactSquareSymmetryIncludingAlpha() throws Exception {
        for (int frame = 0; frame < 16; frame++) {
            for (String name : new String[]{"core_" + frame, "burst_" + frame}) {
                var image = content(name, 72, 72);
                assertMirrors(image, name);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(image.getRGB(x, y), image.getRGB(y, x),
                            name + " diagonal symmetry at " + x + "," + y);
                    }
                }
                assertTrue(visiblePixels(image) > 0, name + " must contain visible pixels");
            }
        }
    }

    @Test
    void runesFullBarAndAllFourFrameCornersRemainExactlyMirrored() throws Exception {
        for (int rune = 1; rune <= 3; rune++) {
            var image = content("rune_" + rune, 20, 20);
            assertMirrors(image, "rune_" + rune);
            assertTrue(visiblePixels(image) > 0);
        }
        assertMirrors(content("bar_180", 180, 8), "full progress bar");
        var empty = ImageIO.read(texture("bar_0").toFile());
        for (int y = 2; y < empty.getHeight(); y++) {
            for (int x = 0; x < empty.getWidth(); x++) {
                assertEquals(0, empty.getRGB(x, y), "An empty bar must contain no visible or hidden RGB pixels");
            }
        }
        var frame = content("frame", 224, 240);
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                assertEquals(frame.getRGB(x, y), frame.getRGB(frame.getWidth() - 1 - x, y),
                    "Frame horizontal symmetry at " + x + "," + y);
            }
        }
        assertTrue(visiblePixels(frame.getSubimage(0, 0, 24, 24)) > 0, "Corner decoration must remain visible");
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int pixel = frame.getRGB(x, y);
                assertEquals(pixel, frame.getRGB(223 - x, y), "Top corners differ");
                assertEquals(pixel, frame.getRGB(x, 239 - y), "Left corners differ");
                assertEquals(pixel, frame.getRGB(223 - x, 239 - y), "Opposite corners differ");
            }
        }
        // Decoration stays inside its canvas; the chamfered outline must not be cut off at any edge.
        for (int x = 0; x < frame.getWidth(); x++) {
            assertEquals(0, frame.getRGB(x, 0) >>> 24, "Top frame outline touches its clipping boundary");
            assertEquals(0, frame.getRGB(x, frame.getHeight() - 1) >>> 24,
                "Bottom frame outline touches its clipping boundary");
        }
        for (int y = 0; y < frame.getHeight(); y++) {
            assertEquals(0, frame.getRGB(0, y) >>> 24, "Left frame outline touches its clipping boundary");
            assertEquals(0, frame.getRGB(frame.getWidth() - 1, y) >>> 24,
                "Right frame outline touches its clipping boundary");
        }
    }

    @Test
    void everyFillStateAddsExactlyOneContinuousPixelColumnWithoutSegmentGaps() throws Exception {
        for (int fill = 0; fill <= 180; fill++) {
            var image = content("bar_" + fill, 180, 8);
            for (int x = 0; x < 180; x++) {
                assertEquals(x < fill ? 220 : 0, image.getRGB(x, 3) >>> 24,
                    "Continuous bar width differs at fill " + fill + ", column " + x);
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

    private static void assertMirrors(final BufferedImage image, final String name) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                assertEquals(pixel, image.getRGB(image.getWidth() - 1 - x, y),
                    name + " horizontal symmetry at " + x + "," + y);
                assertEquals(pixel, image.getRGB(x, image.getHeight() - 1 - y),
                    name + " vertical symmetry at " + x + "," + y);
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
