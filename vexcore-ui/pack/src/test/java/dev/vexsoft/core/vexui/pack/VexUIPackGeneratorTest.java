package dev.vexsoft.core.vexui.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.paper.screenui.progression.ProgressionLayout;
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
        assertTrue(Files.readString(first.resolve("pack.mcmeta")).contains("[97,1]"));
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
        assertTrue(properties.containsKey("text.9671"), "The unlock indicator needs the diamond glyph");
        var frame = ImageIO.read(first.resolve("assets/vexcore/textures/reward/frame.png").toFile());
        assertEquals(224, frame.getWidth());
        assertEquals(42, frame.getHeight());
        assertEquals(0x01565831, frame.getRGB(0, 0));
        assertEquals(0x01434F52, frame.getRGB(0, 1));
        assertEquals(VexUITheme.PANEL.background(), frame.getRGB(100, 20) & 0xFFFFFF);
        assertEquals(VexUITheme.PANEL.lightBorder(), frame.getRGB(100, 3) & 0xFFFFFF);
        var json = new ObjectMapper();
        var spaces = json.readTree(first.resolve("assets/vexcore/font/vexui/space.json").toFile());
        var edge = spaces.get("providers").get(1);
        assertEquals("\uE040", edge.get("chars").get(0).asText());
        assertEquals(7, edge.get("ascent").asInt());
        assertEquals(9, edge.get("height").asInt());
        var bounds = ImageIO.read(first.resolve("assets/vexcore/textures/vexui/hotspot_edge.png").toFile());
        assertEquals(1, bounds.getWidth());
        assertEquals(9, bounds.getHeight());
        for (int y = 0; y < bounds.getHeight(); y++) {
            assertEquals(0, bounds.getRGB(0, y) >>> 24);
        }
        for (int offset : new int[]{0, 899}) {
            var tiles = json.readTree(first.resolve("assets/vexcore/font/vexui/tiles/y_" + offset + ".json").toFile())
                .get("providers");
            assertEquals(16, tiles.size());
            int glyphs = 0;
            for (var tile : tiles) {
                assertEquals(7 - offset, tile.get("ascent").asInt());
                assertEquals(9, tile.get("height").asInt());
                glyphs += tile.get("chars").size();
            }
            assertEquals(27, glyphs);
        }
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
                var frameFont = json.readTree(first.resolve("assets/vexcore/font/reward/frame/"
                    + layout.variant() + ".json").toFile());
                assertEquals(layout.ascent(1), frameFont.get("providers").get(0).get("ascent").asInt());
            }
        }
        for (int part = 0; part < ProgressionLayout.PARTS; part++) {
            var font = json.readTree(first.resolve("assets/vexcore/font/progression/" + part + ".json").toFile());
            for (var provider : font.get("providers")) {
                assertEquals(ProgressionLayout.ascent(part), provider.get("ascent").asInt());
            }
        }
        assertFalse(Files.exists(first.resolve("assets/vexcore/font/reward/text")));
        assertFalse(Files.exists(first.resolve("assets/vexcore/font/reward/quantity")));
        var full = json.readTree(first.resolve("assets/vexcore/font/reward/text.json").toFile()).get("providers");
        for (var provider : full) {
            assertEquals(RewardCardLayout.textAscent(false), provider.get("ascent").asInt());
        }
        var quantity = json.readTree(first.resolve("assets/vexcore/font/reward/quantity.json").toFile())
            .get("providers").get(0);
        assertEquals(RewardCardLayout.textAscent(true), quantity.get("ascent").asInt());
        verifySubset(first, json, full, "reward/quantity", "0123456789+.abcdefghijklmnopqrstuvwxyz");
        for (int part = 1; part <= 5; part++) {
            verifySubset(first, json, full, "progression/" + part, "0123456789-◇");
        }
        verifySubset(first, json, full, "progression/6", "✦");
        verifySubset(first, json, full, "progression/8", "✦");
        for (int part : new int[]{0, ProgressionLayout.EXPERIENCE_TEXT_PART}) {
            var providers = json.readTree(first.resolve("assets/vexcore/font/progression/" + part + ".json").toFile())
                .get("providers");
            assertEquals(full.size(), providers.size());
            for (int index = 0; index < full.size(); index++) {
                assertEquals(full.get(index).get("file"), providers.get(index).get("file"));
            }
        }
    }

    private static void verifySubset(
        Path root, ObjectMapper json, JsonNode full, String font, String characters
    ) throws Exception {
        var providers = json.readTree(root.resolve("assets/vexcore/font/" + font + ".json").toFile()).get("providers");
        assertEquals(1, providers.size());
        var provider = providers.get(0);
        assertEquals(characters, provider.get("chars").get(0).asText());
        assertEquals(22, provider.get("height").asInt());
        var atlas = ImageIO.read(texture(root, provider).toFile());
        int[] codePoints = characters.codePoints().toArray();
        assertEquals(codePoints.length * 18, atlas.getWidth());
        assertEquals(22, atlas.getHeight());
        for (int index = 0; index < codePoints.length; index++) {
            assertArrayEquals(glyph(root, full, codePoints[index]),
                atlas.getRGB(index * 18, 0, 18, 22, null, 0, 18));
        }
    }

    private static int[] glyph(Path root, JsonNode providers, int point) throws Exception {
        for (var provider : providers) {
            var rows = provider.get("chars");
            for (int row = 0; row < rows.size(); row++) {
                int[] points = rows.get(row).asText().codePoints().toArray();
                for (int column = 0; column < points.length; column++) {
                    if (points[column] == point) {
                        var atlas = ImageIO.read(texture(root, provider).toFile());
                        return atlas.getRGB(column * 18, row * 22, 18, 22, null, 0, 18);
                    }
                }
            }
        }
        throw new AssertionError("Missing original glyph " + point);
    }

    private static Path texture(Path root, JsonNode provider) {
        String[] path = provider.get("file").asText().split(":", 2);
        return root.resolve("assets/" + path[0] + "/textures/" + path[1]);
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
