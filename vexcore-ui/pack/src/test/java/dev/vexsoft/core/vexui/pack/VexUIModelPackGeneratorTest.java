package dev.vexsoft.core.vexui.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises inherited vanilla cube geometry, generated layers, special-model reporting and manifest metrics. */
public final class VexUIModelPackGeneratorTest {

    @TempDir
    Path temporary;

    @Test
    public void neutralVanillaEquipmentAndAnimatedItemsResolveTheirActualModel() throws Exception {
        Path output = temporary.resolve("pack");
        Path manifest = temporary.resolve("plugin/models.properties");
        try (var generator = new VexUIModelPackGenerator(Path.of(System.getProperty("vexui.client")), List.of())) {
            generator.generate(output, manifest,
                List.of("minecraft:iron_chestplate", "minecraft:fishing_rod", "minecraft:compass"));
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        assertEquals("12", properties.getProperty("count"));
        assertTrue(Files.readString(manifest.resolveSibling("vexui-model-unsupported.txt")).isBlank());
        for (String item : List.of("iron_chestplate", "fishing_rod", "compass")) {
            var image = modelImage(output, properties, "minecraft:" + item);
            assertTrue(IntStream.range(0, 48).anyMatch(y ->
                IntStream.range(0, 48).anyMatch(x -> (image.getRGB(x, y) >>> 24) > 0)));
        }
    }

    @Test
    public void resolvesInheritedModelsAndBakesNestedPreviewsAtAllSupportedSizes() throws Exception {
        Path definitions = temporary.resolve("source/assets/test/items");
        Files.createDirectories(definitions);
        Files.writeString(definitions.resolve("block.json"),
            """
            {"model":{"type":"minecraft:model","model":"minecraft:block/stone"}}
            """);
        Files.writeString(definitions.resolve("flat.json"),
            """
            {"model":{"type":"minecraft:model","model":"minecraft:item/potato"}}
            """);
        Files.writeString(definitions.resolve("dynamic.json"),
            """
            {"model":{"type":"minecraft:select"}}
            """);
        Path output = temporary.resolve("pack");
        Path manifest = temporary.resolve("plugin/models.properties");
        try (var generator = new VexUIModelPackGenerator(Path.of(System.getProperty("vexui.client")),
            List.of(temporary.resolve("source")))) {
            generator.generate(output, manifest);
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        assertEquals("8", properties.getProperty("count"));
        var cube = modelImage(output, properties, "test:block");
        assertEquals(48, cube.getWidth());
        assertNotEquals(0, cube.getRGB(24, 24) >>> 24);
        assertEquals(0, cube.getRGB(0, 0) >>> 24);
        assertNotEquals(cube.getRGB(14, 26), cube.getRGB(34, 26), "Cube faces retain directional shading");
        for (int pixels : List.of(14, 16, 32, 48)) {
            String block = modelPrefix(properties, "test:block", pixels);
            String flat = modelPrefix(properties, "test:flat", pixels);
            assertEquals(properties.getProperty(block + "font"), properties.getProperty(flat + "font"));
            assertNotEquals(properties.getProperty(block + "point"), properties.getProperty(flat + "point"));
            for (int offset = 0; offset < 9; offset++) {
                assertTrue(Files.readString(font(output, properties.getProperty(block + "font"), offset))
                    .contains("\"ascent\":" + (7 - offset)));
            }
        }
        assertTrue(Files.readString(manifest.resolveSibling("vexui-model-unsupported.txt")).contains("test:dynamic"));
        assertTrue(Integer.parseInt(properties.getProperty("model.0.advance")) > 1);
    }

    private static BufferedImage modelImage(Path output, Properties properties, String item) throws IOException {
        String prefix = modelPrefix(properties, item, 48);
        int point = Integer.parseInt(properties.getProperty(prefix + "point"));
        var provider = new ObjectMapper().readTree(font(output, properties.getProperty(prefix + "font"), 0).toFile())
            .get("providers").get(0);
        String[] texture = provider.get("file").asText().split(":", 2);
        var image = ImageIO.read(output.resolve("assets/" + texture[0] + "/textures/" + texture[1]).toFile());
        var rows = provider.get("chars");
        for (int row = 0; row < rows.size(); row++) {
            int[] points = rows.get(row).asText().codePoints().toArray();
            for (int column = 0; column < points.length; column++) {
                if (points[column] == point) {
                    return image.getSubimage(column * 48, row * 48, 48, 48);
                }
            }
        }
        throw new AssertionError("No atlas cell for " + item);
    }

    private static Path font(Path output, String font, int offset) {
        String[] key = font.split(":", 2);
        return output.resolve("assets/" + key[0] + "/font/" + key[1] + "/y_" + offset + ".json");
    }

    private static String modelPrefix(Properties properties, String model, int pixels) {
        int count = Integer.parseInt(properties.getProperty("count"));
        for (int index = 0; index < count; index++) {
            String prefix = "model." + index + ".";
            if (model.equals(properties.getProperty(prefix + "key"))
                && Integer.toString(pixels).equals(properties.getProperty(prefix + "pixels"))) {
                return prefix;
            }
        }
        throw new AssertionError("No manifest entry for " + model + " at size " + pixels);
    }
}
