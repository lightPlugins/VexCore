package dev.vexsoft.core.vexui.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies reproducible assets, standalone versioning and the absence of copied vanilla textures. */
public final class VexUIPackGeneratorTest {

    @TempDir
    Path temporary;

    @Test
    public void repeatedGenerationIsByteStableAndKeepsVanillaTexturesInTheClient() throws Exception {
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        Path metrics = temporary.resolve("metrics");
        Path client = Path.of(System.getProperty("vexui.client"));
        Path unicode = Path.of(System.getProperty("vexui.unicode"));
        VexUIPackGenerator.generate(first, metrics, client, unicode);
        VexUIPackGenerator.generate(second, temporary.resolve("second-metrics"), client, unicode);
        assertEquals(hashes(first), hashes(second));
        assertFalse(Files.exists(first.resolve("assets/minecraft")));
        assertTrue(Files.readString(first.resolve("assets/vexcore/font/vexui/icons/y_0.json"))
            .contains("minecraft:item/potato.png"));
        assertTrue(Files.readString(first.resolve("pack.mcmeta")).contains("[88,0]"));
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(first.resolve("vexui-manifest.properties"))) {
            properties.load(reader);
        }
        assertEquals("3", properties.getProperty("format"));
        assertEquals("minecraft:default", properties.getProperty("text.source"));
        assertEquals("6", properties.getProperty("text.65"));
        assertEquals("2", properties.getProperty("text.105"));
        assertEquals("4", properties.getProperty("text.32"));
        for (int offset = 0; offset < 9; offset++) {
            String text = Files.readString(first.resolve("assets/vexcore/font/vexui/text/y_" + offset + ".json"));
            assertTrue(text.contains("minecraft:font/ascii.png"));
            assertTrue(text.contains("minecraft:font/accented.png"));
            assertTrue(text.contains("minecraft:font/nonlatin_european.png"));
            assertFalse(text.contains("vexcore:vexui/text.png"));
        }
        assertFalse(Files.exists(first.resolve("VEXUI-FONT-LICENSE.txt")));
        assertEquals(Files.readString(first.resolve("vexui-manifest.properties")),
            Files.readString(metrics.resolve("dev/vexsoft/core/paper/vexui/metrics.properties")));
        try (var files = Files.walk(first.resolve("assets/vexcore/textures"))) {
            assertTrue(files.filter(path -> path.toString().endsWith(".png")).count() > 27);
        }
        assertTrue(properties.containsKey("text.10022"), "The rarity star must use its actual Unicode glyph");
        var frame = ImageIO.read(first.resolve("assets/vexcore/textures/reward/frame.png").toFile());
        assertEquals(224, frame.getWidth());
        assertEquals(42, frame.getHeight());
        assertEquals(0x01565831, frame.getRGB(0, 0));
        assertEquals(0x01434F52, frame.getRGB(0, 1));
        assertEquals(VexUITheme.PANEL.background(), frame.getRGB(100, 20) & 0xFFFFFF);
        assertEquals(VexUITheme.PANEL.lightBorder(), frame.getRGB(100, 3) & 0xFFFFFF);
        var json = new ObjectMapper();
        var sounds = json.readTree(first.resolve("assets/vexcore/sounds.json").toFile());
        for (String name : List.of("collect", "pick-up", "level-up", "upgrade-success", "upgrade-failed")) {
            var event = sounds.get("ui." + name).get("sounds").get(0);
            assertEquals("vexcore:ui/" + name, event.get("name").asText());
            assertFalse(event.get("stream").asBoolean());
            byte[] sound = Files.readAllBytes(first.resolve("assets/vexcore/sounds/ui/" + name + ".ogg"));
            assertEquals("OggS", new String(sound, 0, 4, StandardCharsets.US_ASCII));
            assertTrue(new String(sound, StandardCharsets.ISO_8859_1).contains("\u0001vorbis"));
        }
        for (int slot = 0; slot < 4; slot++) {
            for (int previous = 0; previous < 4; previous++) {
                var layout = new RewardCardLayout(slot, previous, 0, 0, false);
                for (String kind : new String[]{"text", "quantity", "frame"}) {
                    var font = json.readTree(first.resolve("assets/vexcore/font/reward/" + kind + "/"
                        + layout.variant() + ".json").toFile());
                    int primitive = kind.equals("text") ? 0 : kind.equals("quantity") ? 3 : 1;
                    for (var provider : font.get("providers")) {
                        assertEquals(layout.ascent(primitive), provider.get("ascent").asInt());
                    }
                }
            }
        }
    }

    private Map<String, String> hashes(final Path root) throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                hashes.put(root.relativize(path).toString(), digest(path));
            }
        }
        return hashes;
    }

    private String digest(final Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
